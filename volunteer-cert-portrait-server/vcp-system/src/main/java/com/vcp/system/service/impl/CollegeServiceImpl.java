package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.system.dto.CollegeSaveDTO;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.entity.SysDict;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.mapper.SysDictMapper;
import com.vcp.system.service.CollegeService;
import com.vcp.system.service.OrgCollegeCountPort;
import com.vcp.system.vo.CollegeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.vcp.common.util.NumberUtils.toLong;
import static com.vcp.common.util.StringUtils.trimToNull;

/**
 * 学院管理服务实现。
 *
 * <p>四处设计取舍：
 * <ol>
 *   <li><b>学院即字典行</b>：读写都落在 {@code sys_dict}（{@code dict_type = 'college'}），
 *       不新建表 —— 注册页下拉、注册时的学院校验、按学院筛学生与组织，全都按「学院名」
 *       这一列匹配，多一张表只会多一份可能与字典漂移的名字清单。</li>
 *   <li><b>列表不过滤 status</b>：管理页要能看到停用项并把它启用回来。
 *       只返回启用项的是注册页那份 {@code /api/v1/auth/colleges}
 *       （走 {@link DictServiceImpl#listByType}），两处口径的差别是刻意的。</li>
 *   <li><b>计数不逐行查</b>：学生数走一次 GROUP BY，组织数走端口的一次 GROUP BY。
 *       学院通常十几行，但逐行 COUNT 依然是 N+1，而且组织数还要跨模块取。</li>
 *   <li><b>删除是物理删除</b>：{@code sys_dict} 没有 deleted 列，字典行的下架手段本来是停用，
 *       因此删除只用于「建错了、还没被引用」的行，删前必须过占用校验。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollegeServiceImpl implements CollegeService {

    /** 学院字典的类型码，与 sql/10_base_and_test_accounts.sql 的种子数据一致 */
    private static final String DICT_TYPE_COLLEGE = "college";

    /** 字典状态：1 启用 / 0 停用，与 DictServiceImpl 的口径一致 */
    private static final int STATUS_ENABLED = 1;

    private static final int STATUS_DISABLED = 0;

    /** 新增学院的标签色调：学院是中性配置项，不参与状态标签的红绿语义 */
    private static final String TONE_MUTE = "mute";

    /** 学院名称长度上限，与前端表单校验逐字一致（超长会撑坏列表列宽） */
    private static final int NAME_MAX_LENGTH = 30;

    /** 首个学院的排序值：这类字典里 sort 从 1 起，与种子数据一致 */
    private static final int SORT_FIRST = 1;

    /** 前端 status 的字符串口径，与用户启停共用 StatusUpdateDTO */
    private static final String CODE_ENABLED = "ACTIVE";

    private static final String CODE_DISABLED = "DISABLED";

    private final SysDictMapper dictMapper;

    private final StudentInfoMapper studentInfoMapper;

    /**
     * 组织计数端口。
     *
     * <p>用 ObjectProvider 而不是直接注入：端口实现落在 vcp-org，直接注入会把
     * vcp-system 对 vcp-org 的依赖写死；取不到时 orgCount 一律 0，学院列表照常返回。
     * 与 {@link AuthServiceImpl} 取 {@code OrgLookupPort} 是同一个套路。
     */
    private final ObjectProvider<OrgCollegeCountPort> orgCollegeCountProvider;

    /**
     * 取全部学院（含停用项），并补上学生数与组织数。
     *
     * @return 学院列表，按 sort 升序、再按 id 升序
     */
    @Override
    public List<CollegeVO> listColleges() {
        List<SysDict> colleges = dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, DICT_TYPE_COLLEGE)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId));

        // 两次聚合都在循环外取全量，循环里只做 Map 查找
        Map<String, Long> studentCounts = countStudentsByCollege();
        Map<String, Long> orgCounts = countOrgsByCollege();

        List<CollegeVO> result = new ArrayList<>(colleges.size());
        for (SysDict college : colleges) {
            result.add(toVO(college, studentCounts, orgCounts));
        }
        return result;
    }

    /**
     * 新增学院。
     *
     * @param dto 学院名称与排序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCollege(CollegeSaveDTO dto) {
        String name = requireName(dto);
        requireNameAvailable(name);

        SysDict college = new SysDict();
        college.setDictType(DICT_TYPE_COLLEGE);
        // dict_key 与 dict_value 都写学院名：这一类字典没有英文码，硬造一套 COLLEGE_06
        // 只会让字典与实际入库的 student_info.college 对不上（见 sql/10 的注释）。
        college.setDictKey(name);
        college.setDictValue(name);
        college.setTone(TONE_MUTE);
        college.setStatus(STATUS_ENABLED);
        college.setSort(resolveSort(dto.getSort()));
        dictMapper.insert(college);

        log.info("[学院] 新增学院。id={}, name={}, sort={}", college.getId(), name, college.getSort());
    }

    /**
     * 删除学院（物理删除）。
     *
     * @param id 学院对应的 sys_dict.id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCollege(Long id) {
        SysDict college = requireCollege(id);
        String name = college.getDictKey();

        requireNotInUse(countStudents(name), countOrgs(name));

        dictMapper.deleteById(college.getId());
        log.info("[学院] 删除学院。id={}, name={}", college.getId(), name);
    }

    /**
     * 启用 / 停用学院。
     *
     * <p><b>刻意不校验占用</b>：学院合并或停招时，硬删会被占用校验挡住，
     * 只能靠停用让它从注册页下拉里消失（{@link DictServiceImpl#listByType} 只返回
     * {@code status = 1} 的项），已有学生与组织的数据不受影响 —— 这正是本接口存在的理由。
     *
     * @param id  学院对应的 sys_dict.id
     * @param dto 目标状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, StatusUpdateDTO dto) {
        SysDict college = requireCollege(id);
        int status = parseStatus(dto == null ? null : dto.getStatus());

        // 只 patch status 一个字段：updateById 只写非 null 列，
        // 其余列留 null 就不会被回写，学院名与 sort 不受影响。
        SysDict patch = new SysDict();
        patch.setId(college.getId());
        patch.setStatus(status);
        dictMapper.updateById(patch);

        log.info("[学院] 启停学院。id={}, name={}, status={}", college.getId(), college.getDictKey(), status);
    }

    /**
     * 取学院字典行，取不到或不是学院时抛 10001。
     *
     * <p>必须连 {@code dict_type} 一起校验：{@code sys_dict} 里全是字典行，
     * 只按 id 取会让「拿状态码的 id 来删学院」变成删除一条状态字典，
     * 前端看不出异常、系统却少了一个状态码。
     *
     * @param id 字典行 id
     * @return 学院字典行
     * @throws BusinessException 行不存在或类型不是 college（10001）
     */
    private SysDict requireCollege(Long id) {
        SysDict college = id == null ? null : dictMapper.selectById(id);
        if (college == null || !DICT_TYPE_COLLEGE.equals(college.getDictType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "学院不存在");
        }
        return college;
    }

    /**
     * 取并校验学院名称。
     *
     * @param dto 请求体
     * @return 去掉首尾空白的学院名
     * @throws BusinessException 名称为空（10001）或超过 30 字（10001）
     */
    private static String requireName(CollegeSaveDTO dto) {
        String name = dto == null ? null : trimToNull(dto.getName());
        if (name == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写学院名称");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "学院名称最多 30 个字");
        }
        return name;
    }

    /**
     * 学院名唯一性校验。
     *
     * <p>库里 {@code uk_dict_type_key} 是 (dict_type, dict_key) 的唯一约束，
     * 但撞上它抛的是 DuplicateKeyException，会被全局处理器兜成 10000「系统繁忙」；
     * 所以先查一次给出可读文案。并发下仍可能撞约束，那种情况按系统错误处理即可 ——
     * 这里的查询不是为了替代约束，而是为了让常规路径有话说。
     *
     * @param name 已去空格的学院名
     * @throws BusinessException 同名学院已存在（10001）
     */
    private void requireNameAvailable(String name) {
        Long count = dictMapper.selectCount(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, DICT_TYPE_COLLEGE)
                .eq(SysDict::getDictKey, name));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该学院已存在");
        }
    }

    /**
     * 占用校验：学院下还有学生或组织时拒绝删除。
     *
     * <p>文案带具体数量：只说「不能删除」用户会反复试，说清「还有 3 名学生」
     * 才知道该先去哪里清理。错误码沿用 10001 并用自定义文案覆盖，
     * 不新增码值（与活动分类删除时用 CATEGORY_IN_USE 覆盖文案是同一做法）。
     *
     * @param studentCount 该学院下的学生数
     * @param orgCount     该学院下的组织数
     * @throws BusinessException 学生数或组织数大于 0（10001）
     */
    private static void requireNotInUse(long studentCount, long orgCount) {
        if (studentCount > 0 && orgCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "该学院下还有 " + studentCount + " 名学生、" + orgCount + " 个组织，不能删除");
        }
        if (studentCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "该学院下还有 " + studentCount + " 名学生，不能删除");
        }
        if (orgCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "该学院下还有 " + orgCount + " 个组织，不能删除");
        }
    }

    /**
     * 解析前端传来的状态值。
     *
     * <p>请求体复用用户启停的 {@link StatusUpdateDTO}（字段是 String），而学院页面
     * 直接回传列表里的 1 / 0，两种口径都要认。认不出的值一律报错而不是「猜一个默认值」：
     * 猜错会把停用做成启用，用户看到的是「点了没反应」，比报错更难排查。
     *
     * @param raw 前端传入的状态值，如 "1" / "0" / "ACTIVE" / "DISABLED"
     * @return 1 启用 / 0 停用
     * @throws BusinessException 值无法识别（10001）
     */
    private static int parseStatus(String raw) {
        String status = raw == null ? null : raw.trim();
        if (CODE_ENABLED.equals(status) || String.valueOf(STATUS_ENABLED).equals(status)) {
            return STATUS_ENABLED;
        }
        if (CODE_DISABLED.equals(status) || String.valueOf(STATUS_DISABLED).equals(status)) {
            return STATUS_DISABLED;
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "学院状态不合法");
    }

    /**
     * 排序缺省值：排在现有学院之后。
     *
     * <p>不用「行数 + 1」：学院可以被删掉，行数与最大 sort 会脱节
     * （5 个学院里删掉 sort = 5 那行，行数是 4，此时按行数补位得到的 sort 会与现有值重复、
     * 新增的学院插到列表中间），取最大 sort 加一才总是排在末尾。
     *
     * @param sort 前端传入的排序值，可为 null
     * @return 最终落库的排序值
     */
    private Integer resolveSort(Integer sort) {
        if (sort != null && sort > 0) {
            return sort;
        }
        SysDict last = dictMapper.selectOne(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, DICT_TYPE_COLLEGE)
                .orderByDesc(SysDict::getSort)
                .last("LIMIT 1"));
        return last == null || last.getSort() == null ? SORT_FIRST : last.getSort() + 1;
    }

    /**
     * 一次 GROUP BY 取「学院 → 学生数」。
     *
     * <p>不逐学院 COUNT：学院列表页一次要全部学院的人数，逐行查就是 N+1。
     * 查询没有手写 {@code deleted = 0} —— {@code StudentInfo.deleted} 标了
     * {@code @TableLogic}，MyBatis-Plus 会给 selectMaps 的 WHERE 自动拼上该条件，
     * 手写只会拼出重复条件。
     *
     * @return 学院名 → 学生数；学院名为 null 或空串的行不出现在结果里
     */
    private Map<String, Long> countStudentsByCollege() {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : studentInfoMapper.selectMaps(Wrappers.<StudentInfo>query()
                .select("college", "COUNT(*) AS cnt")
                .isNotNull("college")
                .ne("college", "")
                .groupBy("college"))) {
            Object college = row.get("college");
            if (college != null) {
                result.put(String.valueOf(college), toLong(row.get("cnt")));
            }
        }
        return result;
    }

    /**
     * 取「学院 → 组织数」。
     *
     * <p>端口取不到实现、或实现里查询失败时都返回空 Map（orgCount 显示 0）：
     * 缺这一列只影响展示，不该让整个学院列表页报错打不开。
     *
     * @return 学院名 → 组织数；无实现或查询失败时为空 Map
     */
    private Map<String, Long> countOrgsByCollege() {
        OrgCollegeCountPort port = orgCollegeCountProvider.getIfAvailable();
        if (port == null) {
            return Map.of();
        }
        try {
            Map<String, Long> counts = port.countByCollege();
            return counts == null ? Map.of() : counts;
        } catch (Exception e) {
            log.warn("[学院] 统计挂靠组织数失败，orgCount 一律按 0 返回", e);
            return Map.of();
        }
    }

    /**
     * 统计单个学院的学生数，用于删除前的占用校验。
     *
     * @param college 学院名
     * @return 学生数
     */
    private long countStudents(String college) {
        Long count = studentInfoMapper.selectCount(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getCollege, college));
        return count == null ? 0L : count;
    }

    /**
     * 统计单个学院的组织数，用于删除前的占用校验。
     *
     * <p>复用端口的一次全量分组查询，而不是给端口再加一个「按学院名查」的方法：
     * 删除是低频操作，为它把端口接口撑大不划算。
     *
     * @param college 学院名
     * @return 组织数；端口没有实现时返回 0
     */
    private long countOrgs(String college) {
        return countByName(countOrgsByCollege(), college);
    }

    /**
     * 从「学院名 → 计数」的 Map 里按名字取值。
     *
     * <p>不能直接写 {@code counts.getOrDefault(college, 0L)}：端口没有实现时返回的是
     * {@code Map.of()}，而 ImmutableCollections 对 null 键会抛 NPE 而不是返回默认值。
     *
     * @param counts  学院名 → 计数
     * @param college 学院名，允许为 null（字典行的 dict_key 为空时）
     * @return 计数，取不到时返回 0
     */
    private static long countByName(Map<String, Long> counts, String college) {
        return college == null ? 0L : counts.getOrDefault(college, 0L);
    }

    /**
     * 字典行转列表行。
     *
     * @param college       学院字典行
     * @param studentCounts 学院名 → 学生数
     * @param orgCounts     学院名 → 组织数
     * @return 学院列表行
     */
    private CollegeVO toVO(SysDict college, Map<String, Long> studentCounts, Map<String, Long> orgCounts) {
        CollegeVO vo = new CollegeVO();
        vo.setId(college.getId());
        vo.setName(college.getDictKey());
        vo.setSort(college.getSort());
        vo.setStatus(college.getStatus());
        vo.setStudentCount(countByName(studentCounts, college.getDictKey()));
        vo.setOrgCount(countByName(orgCounts, college.getDictKey()));
        return vo;
    }
}
