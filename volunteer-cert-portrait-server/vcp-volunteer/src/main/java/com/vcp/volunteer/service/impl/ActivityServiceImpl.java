package com.vcp.volunteer.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.ActivityStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.org.service.OrgService;
import com.vcp.org.vo.OrgVO;
import com.vcp.volunteer.constant.VolunteerConstants;
import com.vcp.volunteer.dto.ActivityQuery;
import com.vcp.volunteer.dto.ActivitySaveDTO;
import com.vcp.volunteer.dto.ActivityStatusDTO;
import com.vcp.volunteer.entity.ActivityCategory;
import com.vcp.volunteer.entity.VolunteerActivity;
import com.vcp.volunteer.mapper.ActivityCategoryMapper;
import com.vcp.volunteer.mapper.ActivitySignupMapper;
import com.vcp.volunteer.mapper.VolunteerActivityMapper;
import com.vcp.volunteer.mapper.row.ActivityRow;
import com.vcp.volunteer.mapper.row.OrgOverviewRow;
import com.vcp.volunteer.service.ActivityService;
import com.vcp.volunteer.support.VolunteerTimeUtils;
import com.vcp.volunteer.vo.ActivityVO;
import com.vcp.volunteer.vo.OrgOverviewVO;
import com.vcp.volunteer.vo.StatItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 志愿活动服务实现。
 *
 * <p><b>越权一律按「活动不存在」处理</b>：前端把 20003 当成登录失效（清 token 跳登录），
 * 用它表达数据范围会把用户踢下线，也会泄露「这个 id 确实存在」。因此本类里
 * 不在可见范围内的活动统一抛 30001，与 OrgService 里抛 20003 的写法刻意不同。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    /** 活动预计时长的小数位：库里的 duration 是 NUMERIC(10,1)，多写的小数位会被静默四舍五入 */
    private static final int PLANNED_HOURS_SCALE = 1;

    /** 比率（签到率 / 通过率）的小数位，前端 formatPercent 只取 1 位百分数 */
    private static final int RATIO_SCALE = 4;

    /** 单人服务时长的上限，与前端发布表单的校验（0 ~ 24 小时）保持一致 */
    private static final BigDecimal MAX_PLANNED_HOURS = BigDecimal.valueOf(24);

    private final VolunteerActivityMapper activityMapper;

    private final ActivityCategoryMapper categoryMapper;

    private final ActivitySignupMapper signupMapper;

    private final OrgService orgService;

    @Override
    public PageResult<ActivityVO> listActivities(ActivityQuery query) {
        ActivityQuery condition = query == null ? new ActivityQuery() : query;

        Long scopeOrgId = null;
        if (AuthUtils.isOrgAdmin()) {
            scopeOrgId = AuthUtils.getOrgId();
            if (scopeOrgId == null) {
                // 账号没绑定组织时返回空列表，而不是退化成「看到全部组织的活动」
                return PageResult.empty();
            }
            // 数据范围只认登录态：前端传的 orgId 不能用来越权读别的组织
            condition.setOrgId(null);
        }
        boolean excludeDraft = AuthUtils.isStudent();

        IPage<ActivityRow> page = activityMapper.selectActivityPage(
                PageUtils.<ActivityRow>toPage(condition), condition, scopeOrgId, excludeDraft);
        return PageUtils.page(page, this::toVO);
    }

    @Override
    public ActivityVO getActivity(Long id) {
        ActivityRow row = id == null ? null : activityMapper.selectActivityDetail(id);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (AuthUtils.isStudent() && ActivityStatusEnum.DRAFT.getCode().equals(row.getStatus())) {
            // 草稿对学生的可见性：当作不存在，避免泄露别家组织的筹备中活动
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId == null || !orgId.equals(row.getOrgId())) {
                throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
            }
        }
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(ActivitySaveDTO dto) {
        String title = requireTitle(dto);
        requireCategory(dto.getCategoryId());
        LocalDateTime startTime = VolunteerTimeUtils.toDateTime(dto.getDate(), dto.getTime());
        if (startTime == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择活动日期与开始时间");
        }
        String place = trimToNull(dto.getPlace());
        if (place == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写活动地点");
        }
        if (dto.getCapacity() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写招募名额");
        }
        BigDecimal hours = requireHours(dto.getHours());
        LocalDateTime deadline = VolunteerTimeUtils.toDeadline(dto.getDeadline());
        if (deadline == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择报名截止日期");
        }
        if (deadline.isAfter(startTime)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报名截止时间不能晚于活动开始时间");
        }
        String contact = trimToNull(dto.getContact());
        if (contact == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写联系方式");
        }

        VolunteerActivity activity = new VolunteerActivity();
        activity.setTitle(title);
        activity.setCategoryId(dto.getCategoryId());
        activity.setOrgId(requireOwnOrgId());
        activity.setStartTime(startTime);
        activity.setEndTime(endTimeOf(startTime, hours));
        activity.setLocation(place);
        activity.setMaxCount(dto.getCapacity());
        activity.setSignedCount(0);
        activity.setDuration(plannedHours(hours));
        activity.setStatus(ActivityStatusEnum.DRAFT.getCode());
        activity.setDescription(trimToNull(dto.getDescription()));
        activity.setDeadline(deadline);
        activity.setContact(contact);
        activityMapper.insert(activity);

        log.info("[志愿活动] 新建活动草稿。id={}, title={}, orgId={}",
                activity.getId(), title, activity.getOrgId());
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(Long id, ActivitySaveDTO dto) {
        VolunteerActivity activity = requireActivity(id);
        requireWritable(activity);
        if (ActivityStatusEnum.CANCELED.getCode().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "已取消的活动不能再修改");
        }
        String title = requireTitle(dto);

        BigDecimal hours = dto.getHours() == null ? activity.getDuration() : requireHours(dto.getHours());
        LocalDateTime startTime = activity.getStartTime();
        boolean startTimeChanged = dto.getDate() != null || dto.getTime() != null;
        if (startTimeChanged) {
            String date = dto.getDate() != null
                    ? dto.getDate()
                    : DateTimeUtils.formatDate(activity.getStartTime());
            String time = dto.getTime() != null
                    ? dto.getTime()
                    : VolunteerTimeUtils.formatTimeOfDay(activity.getStartTime());
            LocalDateTime newStartTime = VolunteerTimeUtils.toDateTime(date, time);
            if (newStartTime == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择活动日期与开始时间");
            }
            startTime = newStartTime;
        }

        LocalDateTime deadline = activity.getDeadline();
        if (dto.getDeadline() != null) {
            deadline = VolunteerTimeUtils.toDeadline(dto.getDeadline());
            if (deadline != null && startTime != null && deadline.isAfter(startTime)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报名截止时间不能晚于活动开始时间");
            }
        }

        LambdaUpdateWrapper<VolunteerActivity> update = Wrappers.lambdaUpdate();
        update.eq(VolunteerActivity::getId, id);
        update.set(VolunteerActivity::getTitle, title);
        if (dto.getCategoryId() != null) {
            requireCategory(dto.getCategoryId());
            update.set(VolunteerActivity::getCategoryId, dto.getCategoryId());
        }
        if (startTimeChanged) {
            update.set(VolunteerActivity::getStartTime, startTime);
        }
        if (startTime != null && (startTimeChanged || dto.getHours() != null)) {
            update.set(VolunteerActivity::getEndTime, endTimeOf(startTime, hours));
        }
        if (dto.getHours() != null) {
            update.set(VolunteerActivity::getDuration, plannedHours(hours));
        }
        if (dto.getPlace() != null) {
            update.set(VolunteerActivity::getLocation, trimToNull(dto.getPlace()));
        }
        if (dto.getCapacity() != null) {
            update.set(VolunteerActivity::getMaxCount, dto.getCapacity());
        }
        if (dto.getDeadline() != null) {
            update.set(VolunteerActivity::getDeadline, deadline);
        }
        if (dto.getContact() != null) {
            update.set(VolunteerActivity::getContact, trimToNull(dto.getContact()));
        }
        if (dto.getDescription() != null) {
            update.set(VolunteerActivity::getDescription, trimToNull(dto.getDescription()));
        }
        // 用 UpdateWrapper 而不是 updateById：空串要能把字段改回 NULL（updateById 会忽略 null 字段）
        update.set(VolunteerActivity::getUpdateTime, LocalDateTime.now());
        activityMapper.update(null, update);

        log.info("[志愿活动] 修改活动。id={}, title={}", id, title);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, ActivityStatusDTO dto) {
        VolunteerActivity activity = requireActivity(id);
        requireWritable(activity);
        ActivityStatusEnum target = dto == null ? null : ActivityStatusEnum.of(dto.getStatus());
        if (target == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "活动目标状态不合法");
        }
        ActivityStatusEnum current = ActivityStatusEnum.of(activity.getStatus());
        if (current == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "活动当前状态不合法，请先修正数据");
        }
        if (!isTransitionAllowed(current, target)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "活动不能从「" + current.getLabel() + "」变更为「" + target.getLabel() + "」");
        }

        LambdaUpdateWrapper<VolunteerActivity> update = Wrappers.lambdaUpdate();
        update.eq(VolunteerActivity::getId, id);
        update.set(VolunteerActivity::getStatus, target.getCode());
        update.set(VolunteerActivity::getUpdateTime, LocalDateTime.now());
        activityMapper.update(null, update);

        if (target == ActivityStatusEnum.CLOSED) {
            // 结束活动时结算参与情况：确实到场的报名转 COMPLETED，缺勤的保持 APPROVED
            int completed = signupMapper.completeAttendedSignups(id);
            log.info("[志愿活动] 活动结束。id={}, 已结算报名 {} 条", id, completed);
        }
        log.info("[志愿活动] 状态流转。id={}, {} -> {}", id, current.getCode(), target.getCode());
    }

    @Override
    public OrgOverviewVO getOrgOverview(Long orgId) {
        Long targetOrgId = resolveOverviewOrgId(orgId);
        OrgOverviewRow row = activityMapper.selectOrgOverview(targetOrgId);
        if (row == null) {
            row = new OrgOverviewRow();
        }

        OrgVO org = orgService.getOrg(targetOrgId);
        // org 的三个聚合字段由本域回填：vcp-org 侧没有这些数据，不回填页面会显示成 0
        org.setActivities(valueOf(row.getActivityTotal()));
        org.setSignRate(ratio(row.getSignedAttendanceTotal(), row.getAttendanceTotal()));
        org.setPassRate(ratio(row.getApprovedSignupTotal(), auditedSignupTotal(row)));

        List<StatItemVO> stats = new ArrayList<>(6);
        stats.add(stat("activities", "累计活动", row.getActivityTotal(), "场"));
        stats.add(stat("ongoing", "进行中活动", row.getOngoingTotal(), "场"));
        stats.add(stat("pendingSignup", "待审报名", row.getPendingSignupTotal(), "人"));
        stats.add(stat("signRate", "签到率",
                percent(row.getSignedAttendanceTotal(), row.getAttendanceTotal()), "%"));
        stats.add(stat("passRate", "审核通过率",
                percent(row.getApprovedSignupTotal(), auditedSignupTotal(row)), "%"));
        stats.add(stat("unsigned", "未签到人次", row.getUnsignedTotal(), "人次"));

        OrgOverviewVO vo = new OrgOverviewVO();
        vo.setOrg(org);
        vo.setStats(stats);
        return vo;
    }

    /**
     * 解析概览要看的组织：组织管理员只看本组织，学校管理员按参数指定。
     *
     * @param requested 前端传的组织 id
     * @return 实际生效的组织 id
     */
    private Long resolveOverviewOrgId(Long requested) {
        if (AuthUtils.isSchoolAdmin()) {
            if (requested == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请指定要查看的组织");
            }
            return requested;
        }
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId == null) {
                throw new BusinessException(ErrorCodeEnum.ORG_NOT_FOUND, "当前账号未绑定志愿组织，请联系学校管理员");
            }
            return orgId;
        }
        throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
    }

    /**
     * 状态流转白名单。
     *
     * <p>库层面没有任何 CHECK 约束或触发器（见待办 B17），状态机只能靠应用层守：
     * 草稿可发布或取消，已发布可结束或取消，已结束与已取消是终态。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 允许流转时返回 true
     */
    private static boolean isTransitionAllowed(ActivityStatusEnum current, ActivityStatusEnum target) {
        return switch (current) {
            case DRAFT -> target == ActivityStatusEnum.PUBLISHED || target == ActivityStatusEnum.CANCELED;
            case PUBLISHED -> target == ActivityStatusEnum.CLOSED || target == ActivityStatusEnum.CANCELED;
            case CLOSED, CANCELED -> false;
        };
    }

    /**
     * 取活动，不存在或已删除时抛 30001。
     *
     * @param id 活动 id
     * @return 活动实体
     */
    private VolunteerActivity requireActivity(Long id) {
        VolunteerActivity activity = id == null ? null : activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    /**
     * 校验当前用户可写这条活动。
     *
     * @param activity 活动实体
     */
    private void requireWritable(VolunteerActivity activity) {
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId != null && orgId.equals(activity.getOrgId())) {
                return;
            }
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (AuthUtils.isStudent()) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        // 学校管理员可维护任意组织的活动
    }

    /**
     * 取当前组织管理员的组织 id。
     *
     * @return 组织 id
     */
    private Long requireOwnOrgId() {
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId != null) {
                return orgId;
            }
        }
        throw new BusinessException(ErrorCodeEnum.ORG_NOT_FOUND, "当前账号未绑定志愿组织，请联系学校管理员");
    }

    /**
     * 校验分类存在。
     *
     * @param categoryId 分类 id
     */
    private void requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择活动分类");
        }
        ActivityCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND);
        }
    }

    private static String requireTitle(ActivitySaveDTO dto) {
        String title = dto == null ? null : trimToNull(dto.getTitle());
        if (title == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写活动名称");
        }
        return title;
    }

    private static BigDecimal requireHours(BigDecimal hours) {
        if (hours == null || hours.signum() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写单人服务时长");
        }
        if (hours.compareTo(MAX_PLANNED_HOURS) > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "单人服务时长不能超过 24 小时");
        }
        return hours;
    }

    /**
     * 预计时长的入库值（列是 NUMERIC(10,1)）。
     *
     * @param hours 前端填写的时长
     * @return 保留 1 位小数的时长
     */
    private static BigDecimal plannedHours(BigDecimal hours) {
        return hours.setScale(PLANNED_HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 由开始时间与预计时长推导结束时间。
     *
     * <p>发布表单只有「活动日期 + 开始时间 + 单人服务时长」，没有结束时间输入框，
     * 而签到窗口与时长封顶都依赖结束时间，因此统一在这里推导。
     *
     * @param startTime 开始时间
     * @param hours     预计时长（小时）
     * @return 结束时间
     */
    private static LocalDateTime endTimeOf(LocalDateTime startTime, BigDecimal hours) {
        long seconds = hours.multiply(VolunteerConstants.SECONDS_PER_HOUR)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return startTime.plusSeconds(seconds);
    }

    /**
     * 行对象到 VO：日期与时间拆成两个字段，前端直接渲染。
     *
     * @param row 查询行
     * @return 活动 VO
     */
    private ActivityVO toVO(ActivityRow row) {
        ActivityVO vo = new ActivityVO();
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setType(row.getType());
        vo.setDate(DateTimeUtils.formatDate(row.getStartTime()));
        vo.setTime(VolunteerTimeUtils.formatTimeOfDay(row.getStartTime()));
        vo.setPlace(row.getLocation());
        vo.setEnrolled(row.getSignedCount() == null ? 0 : row.getSignedCount());
        vo.setCapacity(row.getMaxCount() == null ? 0 : row.getMaxCount());
        vo.setOrg(row.getOrgName());
        vo.setHours(row.getDuration());
        vo.setStatus(row.getStatus());
        vo.setCategoryId(row.getCategoryId());
        vo.setOrgId(row.getOrgId());
        vo.setDeadline(DateTimeUtils.formatDate(row.getDeadline()));
        vo.setContact(row.getContact());
        vo.setDescription(row.getDescription());
        return vo;
    }

    /**
     * 通过率的分母：已通过 + 已驳回，不含待审核与已取消。
     *
     * @param row 概览计数
     * @return 参与过审核的报名条数
     */
    private static Long auditedSignupTotal(OrgOverviewRow row) {
        return valueOf(row.getApprovedSignupTotal()) + valueOf(row.getRejectedSignupTotal());
    }

    private static Long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 求比率（0 ~ 1 的小数），前端用 formatPercent 渲染成百分数。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率；分母为 0 或缺失时返回 0
     */
    private static BigDecimal ratio(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 求百分数，用于指标卡（卡片上带 % 单位，值本身是 0 ~ 100 的数）。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 百分数，保留 1 位小数
     */
    private static BigDecimal percent(Long numerator, Long denominator) {
        return ratio(numerator, denominator)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 构造指标卡。
     *
     * <p>delta 与 trend 一律留空：库里没有历史快照表，算不出真实的环比，
     * 编一个数字会在答辩时被问住；前端 InkStat 对缺省值不渲染环比那一行。
     *
     * @param key   指标键
     * @param label 指标名称
     * @param value 指标值（计数）
     * @param unit  单位
     * @return 指标项
     */
    private static StatItemVO stat(String key, String label, Long value, String unit) {
        return stat(key, label, BigDecimal.valueOf(valueOf(value)), unit);
    }

    /**
     * 构造指标卡（比率类指标）。
     *
     * @param key   指标键
     * @param label 指标名称
     * @param value 指标值
     * @param unit  单位
     * @return 指标项
     */
    private static StatItemVO stat(String key, String label, BigDecimal value, String unit) {
        StatItemVO item = new StatItemVO();
        item.setKey(key);
        item.setLabel(label);
        item.setValue(value);
        item.setUnit(unit);
        item.setDelta(null);
        item.setTrend(null);
        return item;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
