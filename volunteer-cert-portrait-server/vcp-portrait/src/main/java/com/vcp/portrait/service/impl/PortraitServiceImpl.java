package com.vcp.portrait.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.portrait.dto.PortraitQuery;
import com.vcp.portrait.dto.PortraitRecomputeDTO;
import com.vcp.portrait.entity.StudentProfile;
import com.vcp.portrait.enums.PortraitTagEnum;
import com.vcp.portrait.enums.PublicWelfareLevelEnum;
import com.vcp.portrait.mapper.PortraitAggregateMapper;
import com.vcp.portrait.mapper.StudentProfileMapper;
import com.vcp.portrait.model.ActivitySpanRow;
import com.vcp.portrait.model.CategoryStatRow;
import com.vcp.portrait.model.PortraitRow;
import com.vcp.portrait.model.StudentStatRow;
import com.vcp.portrait.model.TagCountRow;
import com.vcp.portrait.service.PortraitService;
import com.vcp.portrait.vo.PortraitDimensionVO;
import com.vcp.portrait.vo.PortraitRecomputeVO;
import com.vcp.portrait.vo.PortraitVO;
import com.vcp.portrait.vo.TagDistributionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 公益画像服务实现。
 *
 * <p>三类职责，边界刻意分清：
 * <ol>
 *   <li><b>读</b>：画像明细、标签分布、我的画像。只读 {@code student_profile} 快照 +
 *       跨域只读聚合（{@link PortraitAggregateMapper}），不改任何数据；</li>
 *   <li><b>算</b>：等级（按 {@code student_info.total_duration}）与标签（按已完成报名 +
 *       活动分类），规则集中在两个枚举里，本类只做编排；</li>
 *   <li><b>写</b>：重算时刷新 {@code student_profile} 快照与
 *       {@code student_info.public_welfare_level}。</li>
 * </ol>
 *
 * <p><b>重算是幂等的</b>：算出来的内容与库里一致时<b>不写库</b>，因此每日定时兜底不会
 * 把 {@code update_time}（前端展示成「画像生成时间」）刷成「昨晚」而内容其实没变；
 * 返回的 updated 也才是真正有意义的「更新了几份」。
 *
 * <p><b>重算不重算 total_duration</b>：权威时长由 vcp-certification 在审核通过时维护
 * （待办 A5 的结论：以 {@code student_info} 为准），画像只消费它 —— 两个模块各算一遍
 * 迟早会算出两个值。
 *
 * <p><b>重算时机</b>：规则方案 §五 给了两条路 —— 时长审核通过后发事件触发，或
 * 「手动重算接口 + 定时任务兜底」。事件方案需要 vcp-certification 反向依赖 vcp-portrait
 * （与现有依赖方向相反，且本轮约定不改其他模块），故本轮落地第二条：
 * {@code POST /api/v1/portraits/recompute} + {@link com.vcp.portrait.support.PortraitRecomputeJob}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortraitServiceImpl implements PortraitService {

    /** 画像标签在库里的分隔符（student_profile.tags 是逗号分隔串） */
    private static final String TAG_SEPARATOR = ",";

    /** 服务时长维度的满分线（小时）：与前端 mock 的换算一致（50 小时记 100 分） */
    private static final int HOURS_FULL_SCORE = 50;

    /** 活动场次维度的满分线（场）：与前端 mock 的换算一致（15 场记 100 分） */
    private static final int ACTIVITIES_FULL_SCORE = 15;

    /** 持续性维度的满分线（天）：首末次活动跨度 90 天记 100 分 */
    private static final int SPAN_FULL_SCORE_DAYS = 90;

    /** 「长期坚持型」的活动次数下限，与规则方案、04_demo_data.sql 一致 */
    private static final int PERSISTENT_MIN_ACTIVITIES = 3;

    /** 画像快照里时长的精度，对齐 NUMERIC(10,1) */
    private static final int HOURS_SCALE = 1;

    /** 占比的小数位（前端按百分比渲染，只用到 1 位，多留几位给将来展示） */
    private static final int RATIO_SCALE = 4;

    private final StudentProfileMapper profileMapper;

    private final PortraitAggregateMapper aggregateMapper;

    /**
     * 取当前登录学生本人的画像。
     *
     * @return 本人画像
     */
    @Override
    public PortraitVO getMyPortrait() {
        Long studentId = currentStudentId();
        if (studentId == null) {
            // 已登录但没有学生档案（学生注册后档案由学校管理员补录）——
            // 前端命中 50001 会显示「画像尚未生成」的空状态，语义与 mock 一致
            throw new BusinessException(ErrorCodeEnum.PORTRAIT_NOT_GENERATED);
        }
        return loadPortrait(studentId, true);
    }

    /**
     * 分页查询画像明细。
     *
     * @param query 查询条件
     * @return 画像分页结果
     */
    @Override
    public PageResult<PortraitVO> listPortraits(PortraitQuery query) {
        PortraitQuery condition = query == null ? new PortraitQuery() : query;
        Page<PortraitRow> page = aggregateMapper.selectPortraitPage(
                PageUtils.toPage(condition),
                trimToNull(condition.getKeyword()),
                trimToNull(condition.getCollege()),
                trimToNull(condition.getTag()));

        List<PortraitRow> rows = page.getRecords();
        if (rows == null || rows.isEmpty()) {
            // 提前返回同时挡住「空集合 IN ()」：selectCategoryStats 不接受空 id 集合
            return PageResult.of(page.getTotal(), List.of());
        }

        Map<Long, List<CategoryStatRow>> statsByStudent =
                groupByStudent(aggregateMapper.selectCategoryStats(studentIdsOf(rows)));
        return PageUtils.page(page, row -> toVO(row, statsByStudent.get(row.getStudentId()), null, false));
    }

    /**
     * 取指定学生的画像。
     *
     * @param studentId 学生档案 id
     * @return 画像详情
     */
    @Override
    public PortraitVO getPortrait(Long studentId) {
        if (studentId == null) {
            throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND);
        }
        return loadPortrait(studentId, false);
    }

    /**
     * 取画像标签分布。
     *
     * <p>统计口径：先按 {@code student_profile.tags} 在 SQL 里分组，再把「标签组合」拆成单个标签
     * 累加人数 —— 一个学生带 N 个标签就计入 N 项，所以各项之和会大于学生总数（与前端 mock 一致）。
     *
     * @return 标签分布，覆盖全部 8 类标签（无人拥有时为 0），按人数降序
     */
    @Override
    public List<TagDistributionVO> getDistribution() {
        // 分组统计下推到 SQL（GROUP BY tags）：一条查询只返回十几种「标签组合」，而不是把 31 行画像拉回来。
        // 这里刻意不用 profileMapper.selectList(...select(StudentProfile::getTags)) 那种「只取一列」的实体投影：
        // tags 可空，而 MyBatis 的 returnInstanceForEmptyRow 默认为 false，整行映射结果全为 NULL 的行
        // 会以 null 元素进入 List，遍历到就 NPE（2026-09-23 集成验证：31 行里 2 行 tags 为 NULL，接口报 10000）。
        // 现在这条查询的投影带恒非空的 COUNT(*)，null 元素从结构上不可能出现（见 TagCountRow）。
        List<TagCountRow> rows = aggregateMapper.selectTagCounts();

        // 先把 8 个规范标签铺成 0，再累加库里的实际取值：分布图因此始终覆盖完整标签体系，
        // 库里万一有历史遗留的未知标签也不会被悄悄丢掉
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PortraitTagEnum tag : PortraitTagEnum.values()) {
            counts.put(tag.getTagName(), 0);
        }
        // 一行是「一个标签组合 + 该组合的人数」（一个学生可同时有多个标签），拆开后逐个标签累加
        for (TagCountRow row : rows) {
            int students = row.getProfileCount() == null ? 0 : row.getProfileCount();
            for (String tagName : splitTags(row.getTags())) {
                counts.merge(tagName, students, Integer::sum);
            }
        }

        List<TagDistributionVO> result = new ArrayList<>(counts.size());
        counts.forEach((tagName, count) -> {
            PortraitTagEnum tag = PortraitTagEnum.ofTagName(tagName);
            TagDistributionVO vo = new TagDistributionVO();
            vo.setTag(tagName);
            vo.setCount(count);
            vo.setDesc(tag == null ? null : tag.getDescription());
            result.add(vo);
        });
        // 人数多的排前面（前端取 distribution[0] 当「最大标签」），并列时按标签体系的固定顺序；
        // 不用中文名排序 —— 中文串排序依赖 collation，会让不同机器上的展示顺序不一致
        result.sort(Comparator.comparing(TagDistributionVO::getCount).reversed()
                .thenComparingInt(vo -> canonicalOrder(vo.getTag())));
        return result;
    }

    /**
     * 重算画像（等级 + 标签 + 快照）。
     *
     * @param dto 重算范围
     * @return 扫描人数与实际写入份数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortraitRecomputeVO recompute(PortraitRecomputeDTO dto) {
        Long studentId = dto == null ? null : dto.getStudentId();
        List<StudentStatRow> students = aggregateMapper.selectStudentStats(studentId);
        if (students.isEmpty()) {
            if (studentId != null) {
                throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND);
            }
            return new PortraitRecomputeVO(0, 0);
        }

        List<Long> studentIds = students.stream().map(StudentStatRow::getStudentId).toList();
        Map<Long, List<CategoryStatRow>> statsByStudent =
                groupByStudent(aggregateMapper.selectCategoryStats(studentIds));
        Map<Long, StudentProfile> profileByStudent = loadProfiles(studentIds);

        // 整批共用同一个时间点：一次重算写出的画像，「生成时间」应当一致
        LocalDateTime now = LocalDateTime.now();
        // 未映射到标签的分类按分类去重后汇总成一条 WARN，而不是每个学生刷一行日志
        Set<String> unmappedCategories = new TreeSet<>();
        int updated = 0;
        for (StudentStatRow student : students) {
            List<CategoryStatRow> stats = statsByStudent.getOrDefault(student.getStudentId(), List.of());
            if (rebuildOne(student, stats, profileByStudent.get(student.getStudentId()),
                    now, unmappedCategories)) {
                updated++;
            }
        }

        if (!unmappedCategories.isEmpty()) {
            log.warn("[画像] 以下活动分类既没有可识别的 code、中文名也不在标签映射表里，本次未给对应学生"
                    + "写类型标签（不静默丢弃，请补 activity_category.code 或扩展 PortraitTagEnum）：{}",
                    unmappedCategories);
        }
        log.info("[画像] 重算完成。扫描 {} 人，实际写入 {} 份画像", students.size(), updated);
        return new PortraitRecomputeVO(students.size(), updated);
    }

    /**
     * 取单个学生的画像，画像未生成时抛 50001。
     *
     * @param studentId      学生档案 id
     * @param withDimensions 是否附带维度得分（只有「我的画像」需要）
     * @return 画像响应对象
     */
    private PortraitVO loadPortrait(Long studentId, boolean withDimensions) {
        PortraitRow row = aggregateMapper.selectPortrait(studentId);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.PORTRAIT_NOT_GENERATED);
        }
        List<CategoryStatRow> stats = aggregateMapper.selectCategoryStats(List.of(studentId));
        ActivitySpanRow span = withDimensions ? aggregateMapper.selectActivitySpan(studentId) : null;
        return toVO(row, stats, span, withDimensions);
    }

    /**
     * 画像行 + 分类计数 → 响应对象。
     *
     * @param row            画像行
     * @param stats          该学生的分类计数，可为 null
     * @param span           首末次活动时间，仅算维度时用到，可为 null
     * @param withDimensions 是否附带维度得分
     * @return 响应对象
     */
    private static PortraitVO toVO(PortraitRow row, List<CategoryStatRow> stats,
                                   ActivitySpanRow span, boolean withDimensions) {
        List<CategoryStatRow> safeStats = stats == null ? List.of() : stats;
        List<String> tags = splitTags(row.getTags());
        int totalByCategory = safeStats.stream().mapToInt(PortraitServiceImpl::countOf).sum();

        PortraitVO vo = new PortraitVO();
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setStudentNo(row.getStudentNo());
        vo.setCollege(row.getCollege());
        vo.setMajor(row.getMajor());
        vo.setGrade(row.getGrade());
        vo.setTags(tags);
        vo.setTag(primaryTag(tags));
        vo.setLevel(resolveLevelName(row));
        vo.setLevelCode(resolveLevelCode(row));
        // 时长取 student_info.total_duration（权威值）：画像快照那一列只在重算时写入并用于比对
        vo.setTotalHours(row.getTotalDuration());
        vo.setActivityCount(row.getTotalActivities());
        // 占比按「已完成活动次数」实时计算：student_profile 没有这两列，
        // 分子分母取自同一批分类计数，不会出现两个来源拼出来的比例
        vo.setCommunityRatio(ratioOf(safeStats, PortraitTagEnum.COMMUNITY, totalByCategory));
        vo.setEnvironmentRatio(ratioOf(safeStats, PortraitTagEnum.ENVIRONMENT, totalByCategory));
        vo.setCategoryPreference(row.getCategoryPreference());
        vo.setGeneratedAt(DateTimeUtils.formatDateTime(row.getGeneratedAt()));
        if (withDimensions) {
            vo.setDimensions(dimensions(row, safeStats, span, totalByCategory));
        }
        return vo;
    }

    /**
     * 重算单个学生：算等级与标签，写回画像快照，等级有变化时同步写回学生档案。
     *
     * @param student            学生档案的时长与现有等级
     * @param stats              该学生的分类计数
     * @param existing           现有画像行，没有则为 null
     * @param now                本次重算统一使用的时间点
     * @param unmappedCategories 收集「分类 → 标签」映射失败的分类，供最后汇总告警
     * @return 是否写入了数据（新建画像、画像内容变化、或等级变化）
     */
    private boolean rebuildOne(StudentStatRow student, List<CategoryStatRow> stats, StudentProfile existing,
                               LocalDateTime now, Set<String> unmappedCategories) {
        BigDecimal hours = student.getTotalDuration() == null ? BigDecimal.ZERO : student.getTotalDuration();
        int activityCount = stats.stream().mapToInt(PortraitServiceImpl::countOf).sum();

        CategoryStatRow preferred = preferredCategory(stats);
        String preference = preferred == null ? null : preferred.getCategoryName();
        PortraitTagEnum typeTag = null;
        if (preferred != null) {
            // 以 code 为键、中文名兜底；两者都匹配不上就记进告警集合（不静默丢弃）
            typeTag = PortraitTagEnum.resolveTypeTag(preferred.getCategoryCode(), preferred.getCategoryName());
            if (typeTag == null) {
                unmappedCategories.add(describeCategory(preferred));
            }
        }

        // 标签生成顺序与 04_demo_data.sql 的 CONCAT_WS 完全一致：热心志愿者 → 长期坚持型 → 类型标签
        List<String> tags = new ArrayList<>(3);
        if (hours.signum() > 0) {
            tags.add(PortraitTagEnum.ENTHUSIAST.getTagName());
        }
        if (activityCount >= PERSISTENT_MIN_ACTIVITIES) {
            tags.add(PortraitTagEnum.PERSISTENT.getTagName());
        }
        if (typeTag != null) {
            tags.add(typeTag.getTagName());
        }
        String tagsText = tags.isEmpty() ? null : String.join(TAG_SEPARATOR, tags);
        String description = portraitDescription(activityCount, hours);

        boolean written = false;
        if (existing == null) {
            StudentProfile profile = new StudentProfile();
            profile.setStudentId(student.getStudentId());
            profile.setTotalActivities(activityCount);
            profile.setTotalDuration(hours);
            profile.setCategoryPreference(preference);
            profile.setTags(tagsText);
            profile.setPortraitDesc(description);
            profileMapper.insert(profile);
            written = true;
        } else if (changed(existing, activityCount, hours, preference, tagsText, description)) {
            // 用 UpdateWrapper 显式 set 每一列：updateById 的字段策略是「非 null 才更新」，
            // 标签从有到无时（tagsText = null）那一列会被静默跳过，库里留下过期标签。
            // update_time 也显式写：自定义 UPDATE 不经过 VcpMetaObjectHandler（见待办 B17）
            profileMapper.update(null, Wrappers.<StudentProfile>lambdaUpdate()
                    .eq(StudentProfile::getId, existing.getId())
                    .set(StudentProfile::getTotalActivities, activityCount)
                    .set(StudentProfile::getTotalDuration, hours)
                    .set(StudentProfile::getCategoryPreference, preference)
                    .set(StudentProfile::getTags, tagsText)
                    .set(StudentProfile::getPortraitDesc, description)
                    .set(StudentProfile::getUpdateTime, now));
            written = true;
        }

        String levelName = PublicWelfareLevelEnum.of(hours).getLevelName();
        if (!levelName.equals(student.getPublicWelfareLevel())) {
            aggregateMapper.updateWelfareLevel(student.getStudentId(), levelName, now);
            written = true;
        }
        return written;
    }

    /**
     * 取参与次数最多的分类，并列时取分类 id 较小的那个。
     *
     * <p>不用中文名排序打破并列：中文串的排序结果依赖数据库 collation，
     * 换台机器执行结果就会变（规则方案 §三 明确要求）。
     *
     * @param stats 该学生的分类计数，按分类 id 升序（SQL 已排序）
     * @return 偏好分类；没有已完成活动时返回 null
     */
    private static CategoryStatRow preferredCategory(List<CategoryStatRow> stats) {
        CategoryStatRow best = null;
        for (CategoryStatRow stat : stats) {
            if (best == null) {
                best = stat;
                continue;
            }
            int compare = Integer.compare(countOf(stat), countOf(best));
            if (compare > 0 || (compare == 0 && categoryIdOf(stat) < categoryIdOf(best))) {
                best = stat;
            }
        }
        return best;
    }

    /**
     * 判断画像内容是否与库里一致，一致就不写库（重算因此是幂等的）。
     *
     * @param profile       现有画像
     * @param activityCount 本次算出的活动次数
     * @param hours         本次算出的时长
     * @param preference    本次算出的偏好分类
     * @param tagsText      本次算出的标签串
     * @param description   本次算出的画像描述
     * @return 有任何一项不同返回 true
     */
    private static boolean changed(StudentProfile profile, int activityCount, BigDecimal hours,
                                   String preference, String tagsText, String description) {
        return !Objects.equals(profile.getTotalActivities(), activityCount)
                || !sameDecimal(profile.getTotalDuration(), hours)
                || !Objects.equals(profile.getCategoryPreference(), preference)
                || !Objects.equals(profile.getTags(), tagsText)
                || !Objects.equals(profile.getPortraitDesc(), description);
    }

    /**
     * 画像描述文案，与 {@code sql/04_demo_data.sql} 的拼接文案逐字一致
     * （一致才能让「重算」判定为无变化，不产生无谓写入）。
     *
     * @param activityCount 已完成活动次数
     * @param hours         累计有效时长
     * @return 描述文本
     */
    private static String portraitDescription(int activityCount, BigDecimal hours) {
        return "该同学累计参与志愿活动 %d 次，累计有效志愿时长 %s 小时。"
                .formatted(activityCount, formatHours(hours));
    }

    /**
     * 按 NUMERIC(10,1) 的精度输出时长文本（如 5.6、0.0），与 PostgreSQL 的显示一致。
     *
     * @param hours 时长
     * @return 一位小数的文本
     */
    private static String formatHours(BigDecimal hours) {
        BigDecimal value = hours == null ? BigDecimal.ZERO : hours;
        return value.setScale(HOURS_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 画像维度得分（满分 100）。
     *
     * <p>前四项沿用前端 mock 的换算口径（服务时长 50 小时满分、活动场次 15 场满分、
     * 社区/环保按占比折算），第五项「持续性」在 mock 里是写死的 78 分，这里给它一个真实口径：
     * <b>首末次已完成活动的跨度，90 天记满分</b> —— 只参加一次或集中在一周内的学生得分低，
     * 跨学期持续参与的学生得分高。
     *
     * @param row             画像行
     * @param stats           分类计数
     * @param span            首末次活动时间，可为 null
     * @param totalByCategory 分类计数合计
     * @return 维度列表，顺序与前端展示一致
     */
    private static List<PortraitDimensionVO> dimensions(PortraitRow row, List<CategoryStatRow> stats,
                                                       ActivitySpanRow span, int totalByCategory) {
        BigDecimal hours = row.getTotalDuration() == null ? BigDecimal.ZERO : row.getTotalDuration();
        int activityCount = row.getTotalActivities() == null ? 0 : row.getTotalActivities();

        List<PortraitDimensionVO> dimensions = new ArrayList<>(5);
        dimensions.add(new PortraitDimensionVO("服务时长", scoreOf(hours, HOURS_FULL_SCORE)));
        dimensions.add(new PortraitDimensionVO("活动场次",
                scoreOf(BigDecimal.valueOf(activityCount), ACTIVITIES_FULL_SCORE)));
        dimensions.add(new PortraitDimensionVO("社区服务",
                percentOf(ratioOf(stats, PortraitTagEnum.COMMUNITY, totalByCategory))));
        dimensions.add(new PortraitDimensionVO("环保行动",
                percentOf(ratioOf(stats, PortraitTagEnum.ENVIRONMENT, totalByCategory))));
        dimensions.add(new PortraitDimensionVO("持续性",
                scoreOf(BigDecimal.valueOf(spanDays(span)), SPAN_FULL_SCORE_DAYS)));
        return dimensions;
    }

    /**
     * 线性折算成 0~100 分，达到或超过满分线按 100 计。
     *
     * @param value 实际值
     * @param full  满分线
     * @return 0~100 的整数分
     */
    private static int scoreOf(BigDecimal value, int full) {
        if (value == null || value.signum() <= 0) {
            return 0;
        }
        BigDecimal score = value.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(full), 0, RoundingMode.HALF_UP);
        return Math.min(100, score.intValue());
    }

    /**
     * 比例转百分制分数（0.68 → 68）。
     *
     * @param ratio 0~1 的比例
     * @return 0~100 的整数分
     */
    private static int percentOf(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 某标签对应分类的活动占比。
     *
     * @param stats 该学生的分类计数
     * @param tag   类型标签（用它定位分类）
     * @param total 分类计数合计
     * @return 0~1 的比例，合计为 0 时返回 0
     */
    private static BigDecimal ratioOf(List<CategoryStatRow> stats, PortraitTagEnum tag, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        int matched = 0;
        for (CategoryStatRow stat : stats) {
            if (tag.matchesCategory(stat.getCategoryCode(), stat.getCategoryName())) {
                matched += countOf(stat);
            }
        }
        return BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 首末次已完成活动的跨度天数。
     *
     * @param span 首末次时间，可为 null
     * @return 跨度天数；不足两次活动时为 0
     */
    private static long spanDays(ActivitySpanRow span) {
        if (span == null || span.getFirstTime() == null || span.getLastTime() == null) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.DAYS.between(span.getFirstTime(), span.getLastTime()));
    }

    /**
     * 主标签：类型标签优先，其次「长期坚持型」，再次「热心志愿者」。
     *
     * <p>前端表格只有一列、印章只有一枚，需要一个代表性标签；类型标签最能体现学生的参与领域，
     * 行为标签作兜底。{@code tags} 字段仍返回全部标签，信息不丢。
     *
     * @param tags 全部标签
     * @return 主标签；无标签时返回 null
     */
    private static String primaryTag(List<String> tags) {
        for (PortraitTagEnum tag : PortraitTagEnum.values()) {
            if (tag.isTypeTag() && tags.contains(tag.getTagName())) {
                return tag.getTagName();
            }
        }
        if (tags.contains(PortraitTagEnum.PERSISTENT.getTagName())) {
            return PortraitTagEnum.PERSISTENT.getTagName();
        }
        if (tags.contains(PortraitTagEnum.ENTHUSIAST.getTagName())) {
            return PortraitTagEnum.ENTHUSIAST.getTagName();
        }
        // 库里有标签体系里没有的名字（历史遗留）时也照原样展示，不丢
        return tags.isEmpty() ? null : tags.get(0);
    }

    /**
     * 等级名：以库中值为准；尚未算过（为空）时按同一套阈值即时给出，避免页面空着。
     *
     * @param row 画像行
     * @return 等级中文名
     */
    private static String resolveLevelName(PortraitRow row) {
        if (row.getLevel() != null && !row.getLevel().isBlank()) {
            return row.getLevel();
        }
        return PublicWelfareLevelEnum.of(row.getTotalDuration()).getLevelName();
    }

    /**
     * 等级英文码。
     *
     * @param row 画像行
     * @return 等级码；库中值无法识别时按时长重新定档
     */
    private static String resolveLevelCode(PortraitRow row) {
        PublicWelfareLevelEnum level = PublicWelfareLevelEnum.ofName(row.getLevel());
        if (level == null) {
            level = PublicWelfareLevelEnum.of(row.getTotalDuration());
        }
        return level.getCode();
    }

    /**
     * 拆分逗号分隔的标签串。
     *
     * @param tags 标签串，可为 null
     * @return 标签列表；无标签时返回空列表（不是含空串的列表 —— 前端会把空串渲染成一个空标签）
     */
    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : tags.split(TAG_SEPARATOR)) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 标签在固定标签体系里的序号，用于并列排序。
     *
     * @param tagName 标签名
     * @return 序号；未知标签排到最后
     */
    private static int canonicalOrder(String tagName) {
        PortraitTagEnum tag = PortraitTagEnum.ofTagName(tagName);
        return tag == null ? Integer.MAX_VALUE : tag.ordinal();
    }

    /**
     * 按学生分组分类计数。
     *
     * @param stats 分类计数列表
     * @return 学生 id → 分类计数列表
     */
    private static Map<Long, List<CategoryStatRow>> groupByStudent(List<CategoryStatRow> stats) {
        Map<Long, List<CategoryStatRow>> result = new HashMap<>();
        for (CategoryStatRow stat : stats) {
            result.computeIfAbsent(stat.getStudentId(), key -> new ArrayList<>()).add(stat);
        }
        return result;
    }

    /**
     * 取当前页去重后的学生 id。
     *
     * @param rows 画像行列表
     * @return 学生 id 列表
     */
    private static List<Long> studentIdsOf(List<PortraitRow> rows) {
        return rows.stream()
                .map(PortraitRow::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 批量取现有画像行。
     *
     * @param studentIds 学生 id 集合
     * @return 学生 id → 画像行
     */
    private Map<Long, StudentProfile> loadProfiles(Collection<Long> studentIds) {
        Map<Long, StudentProfile> result = new HashMap<>();
        List<StudentProfile> profiles = profileMapper.selectList(Wrappers.<StudentProfile>lambdaQuery()
                .in(StudentProfile::getStudentId, studentIds));
        for (StudentProfile profile : profiles) {
            result.put(profile.getStudentId(), profile);
        }
        return result;
    }

    /**
     * 取当前登录学生本人的学生档案 id。
     *
     * <p>为什么不走 {@link AuthUtils}：它只有 getUserId / getRoleCode / getOrgId，没有
     * getStudentId（本轮约定不改 vcp-framework），因此按它给出的常量
     * {@link AuthUtils#SESSION_KEY_STUDENT_ID} 直接读会话 —— 键名必须用常量，
     * 手写字符串写错不会报错，只会表现为「能登录但查不到自己的画像」。
     *
     * <p>这就是「我的」接口的防越权点：<b>只认会话里的值，不接收前端传入的 studentId</b>。
     *
     * @return 学生档案 id；非学生或档案尚未建立时返回 null
     * @throws BusinessException 未登录（20001）
     */
    private static Long currentStudentId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }
        SaSession session = StpUtil.getSession(false);
        Object studentId = session == null ? null : session.get(AuthUtils.SESSION_KEY_STUDENT_ID);
        return studentId == null ? null : Long.valueOf(studentId.toString());
    }

    /**
     * 描述一个分类，供「未映射到标签」的告警定位。
     *
     * @param stat 分类计数行
     * @return 形如 {@code 分类#7(无编码/乡村支教)} 的描述
     */
    private static String describeCategory(CategoryStatRow stat) {
        String code = stat.getCategoryCode() == null || stat.getCategoryCode().isBlank()
                ? "无编码" : stat.getCategoryCode();
        String name = stat.getCategoryName() == null || stat.getCategoryName().isBlank()
                ? "无名" : stat.getCategoryName();
        return "分类#%d(%s/%s)".formatted(stat.getCategoryId(), code, name);
    }

    /**
     * 分类计数的空安全取值。
     *
     * @param stat 分类计数行
     * @return 活动次数，为 null 时按 0
     */
    private static int countOf(CategoryStatRow stat) {
        return stat.getActivityCount() == null ? 0 : stat.getActivityCount();
    }

    /**
     * 分类 id 的空安全取值。
     *
     * @param stat 分类计数行
     * @return 分类 id，为 null 时按最大值（排序时排最后）
     */
    private static long categoryIdOf(CategoryStatRow stat) {
        return stat.getCategoryId() == null ? Long.MAX_VALUE : stat.getCategoryId();
    }

    /**
     * 两个时长是否相等（按数值比较，忽略 0 与 0.0 的标度差异）。
     *
     * @param left  左侧
     * @param right 右侧
     * @return 数值相等返回 true
     */
    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        BigDecimal a = left == null ? BigDecimal.ZERO : left;
        BigDecimal b = right == null ? BigDecimal.ZERO : right;
        return a.compareTo(b) == 0;
    }

    /**
     * 把空串归一成 null：前端「重置」会把筛选框置成空串，而空串必须当作「不筛选」，
     * 否则会变成「筛选学院为空的学生」这类查不到任何数据的条件（待办 B19）。
     *
     * @param value 原始值
     * @return 去空白后的值；为空时返回 null
     */
    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
