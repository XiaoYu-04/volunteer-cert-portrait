package com.vcp.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.enums.RoleCodeEnum;
import com.vcp.common.enums.UserStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.framework.util.PasswordUtils;
import com.vcp.system.dto.ResetPasswordDTO;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.dto.UserQuery;
import com.vcp.system.dto.UserSaveDTO;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.entity.SysRole;
import com.vcp.system.entity.SysUser;
import com.vcp.system.entity.SysUserRole;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.mapper.SysUserRoleMapper;
import com.vcp.system.service.DictService;
import com.vcp.system.service.UserService;
import com.vcp.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 用户管理服务实现。
 *
 * <p>三处值得说明的取舍：
 * <ol>
 *   <li><b>按角色筛选走两步查询</b>：先用 {@link SysUserRoleMapper#selectUserIdsByRoleCode}
 *       取到用户 id 集合，再作为 IN 条件交给用户表分页。不写 JOIN 是因为分页插件
 *       对自定义 JOIN 的列名与 COUNT 语句有额外要求，两步更稳且结果一致。</li>
 *   <li><b>角色列一次批量补齐</b>：分页查出几十条后再用 {@link RoleResolver#byUserIds}
 *       一次性取角色，避免逐条查造成 N+1。</li>
 *   <li><b>新增用户按角色决定建不建档案</b>：学生角色会顺手建一行 {@code student_info}
 *       （学号与学院的口径见 {@link StudentArchiveRegistrar}），管理员角色不建 ——
 *       档案列表与画像重算都按 {@code student_info} 认人，给管理员建档等于把管理员算成学生。
 *       学院与学号都由表单提交并校验（与注册接口同一条规则），非学生角色两列都忽略。</li>
 *   <li><b>删除用户走销档</b>：不只是删账号，还要级联清理该学生的档案与业务数据
 *       （见 {@link StudentArchivePurger} 与 {@link #deleteUser(Long)}）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 新增用户与重置口令共用的默认口令，与前端表单留空时的提示一致 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 学院字典的类型码，与注册接口用的是同一个（sys_dict.dict_type） */
    private static final String DICT_TYPE_COLLEGE = "college";

    /**
     * 学号规则：4-20 位纯数字，与注册接口（{@code AuthServiceImpl.RE_STUDENT_NO}）
     * 和前端 {@code RegisterView} 的校验一字不差。
     *
     * <p>刻意不写死位数：库内现有学号三种形态并存（{@code 20230001} 八位 /
     * {@code 2023100001} 十位 / {@code S000011} 占位），固定位数会把扩量数据判成非法。
     */
    private static final Pattern RE_STUDENT_NO = Pattern.compile("^[0-9]{4,20}$");

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    /**
     * 学生档案 Mapper：仅用于新增学生前的学号查重。
     *
     * <p>建档本身仍走 {@link StudentArchiveRegistrar}，不在这里直接 insert ——
     * 那是建档规则的唯一落点。
     */
    private final StudentInfoMapper studentInfoMapper;

    /** 建档入口：新增用户被赋予学生角色时补一行 student_info */
    private final StudentArchiveRegistrar studentArchiveRegistrar;

    /** 销档入口：删除用户时级联清理该账号的学生档案、报名、签到与服务时长（待办 B31） */
    private final StudentArchivePurger studentArchivePurger;

    private final RoleResolver roleResolver;

    /** 校验学院是否命中启用中的字典项，注册接口走的是同一处判定 */
    private final DictService dictService;

    /**
     * 分页查询用户列表。
     *
     * <p>三个筛选条件都是可选的：前端"重置"后会把它们置为空串，因此一律先判 hasText，
     * 空串当作不筛选。若直接 {@code eq(role, "")}，页面上会变成"一条都查不到"。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<UserVO> listUsers(UserQuery query) {
        UserQuery condition = query == null ? new UserQuery() : query;
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();

        if (hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            // 用 and(...) 把两个 or 条件括起来，否则 or 会与后面的 eq 平级，
            // 拼出 "username like ? or real_name like ? and status = ?"，
            // 由于 and 优先级更高，实际语义变成"用户名命中即可无视状态筛选"。
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }

        if (hasText(condition.getStatus())) {
            UserStatusEnum status = UserStatusEnum.of(condition.getStatus().trim());
            // 认不出的状态码按"查不到"处理而不是忽略该条件：忽略会让用户以为筛选生效了，
            // 实际拿到的是全量数据，属于静默失效。库里 status 只有 0/1，-1 必然无匹配。
            wrapper.eq(SysUser::getStatus, status == null ? -1 : status.getDbValue());
        }

        if (hasText(condition.getRole())) {
            List<Long> userIds = userRoleMapper.selectUserIdsByRoleCode(condition.getRole().trim());
            if (userIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(SysUser::getId, userIds);
        }

        // 按 id 倒序：新增的用户排在最前，管理员刚建完账号就能看到，
        // 不用翻页去找。逻辑删除由 @TableLogic 自动追加 deleted = 0。
        wrapper.orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(PageUtils.toPage(condition), wrapper);
        Map<Long, SysRole> roleByUser = roleResolver.byUserIds(idsOf(page.getRecords()));
        return PageUtils.page(page, user -> toVO(user, roleByUser.get(user.getId())));
    }

    /**
     * 新增用户并绑定角色。
     *
     * @param dto 新增参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserSaveDTO dto) {
        if (dto == null || !hasText(dto.getUsername())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写用户名");
        }
        if (!hasText(dto.getName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写姓名");
        }

        String username = dto.getUsername().trim();
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名已存在");
        }

        SysRole role = requireRole(dto.getRole());

        // 学生必须带学院：学院是「按学院统计」的分组键，缺了就落进空分组，
        // 而档案一旦建好，本接口没有补录入口（编辑用户只 patch sys_user，不碰 student_info），
        // 所以这里必须挡住，不能像以前那样静默写 null。规则与注册接口同一条，
        // 判定收敛在 DictService.containsEnabled 一处，免得两边漂移出「注册能选、这里不能选」。
        // 学号与学院同进同出：非学生角色两列都保持 null（与下面建档只在学生分支调用是同一套口径）。
        String college = null;
        String studentNo = null;
        if (RoleCodeEnum.STUDENT.getCode().equals(role.getRoleCode())) {
            college = trimToNull(dto.getCollege());
            if (college == null || !dictService.containsEnabled(DICT_TYPE_COLLEGE, college)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择学院");
            }

            // 学号格式校验放 Service 而不是 DTO 的注解上，与注册接口口径一致：
            // 注解的 message 拼装不出前端定死的文案，放这里两边提示才能一字不差。
            studentNo = trimToNull(dto.getStudentNo());
            if (studentNo == null || !RE_STUDENT_NO.matcher(studentNo).matches()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "学号为 4-20 位数字");
            }

            // 学号查重：student_no 上有唯一约束，不先查一次的话撞库只能给出"系统繁忙"，
            // 管理员根本不知道自己填的学号已被占用。
            // 注意查询自带 @TableLogic 的 deleted = 0：逻辑删除过的学号查不出来，
            // 那种学号会一路走到插入、被数据库的唯一约束挡下（约束不认 deleted），
            // 拿到的文案不如这里直白。这是既有取舍，与用户名查重的口径一致，保持一致即可。
            if (existsStudentNo(studentNo)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该学号已存在");
            }
        }

        // 口令策略统一走 PasswordUtils.checkPolicy（6-32 位），返回的文案可直接回显给用户。
        // 必须先校验再编码：超长明文喂给 BCrypt 编码器会被直接抛 IllegalArgumentException，
        // 表现成 500，而不是管理员看得懂的提示。
        String rawPassword = hasText(dto.getPassword()) ? dto.getPassword() : DEFAULT_PASSWORD;
        String policyError = PasswordUtils.checkPolicy(rawPassword);
        if (policyError != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, policyError);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(dto.getName().trim());
        user.setPhone(trimToNull(dto.getPhone()));
        user.setEmail(trimToNull(dto.getEmail()));
        // 口令以 BCrypt 密文入库，同一明文每次哈希结果都不同（盐随机），
        // 因此库里既没有可被直接复用的明文，也无法从密文反推两个账号是否同口令。
        user.setPassword(PasswordUtils.encode(rawPassword));
        user.setStatus(UserStatusEnum.ACTIVE.getDbValue());
        user.setDeleted(0);
        userMapper.insert(user);

        roleResolver.bind(user.getId(), role.getId());

        // 只有学生角色才建档：学校/组织管理员在 student_info 里本来就没有对应行，
        // 顺手建了会让档案列表与画像重算把他们也算成学生。
        // 与上面的插入同处一个事务，建档失败会连账号一起回滚。
        // 学号传上面校验过、且已查过重的那一个。上面的查重与本方法是两个独立动作，
        // 两次请求同时通过查重的窗口由 student_no 的唯一约束兜底（见 StudentArchiveRegistrar）。
        if (RoleCodeEnum.STUDENT.getCode().equals(role.getRoleCode())) {
            studentArchiveRegistrar.ensureArchive(user.getId(), college, studentNo);
        }
    }

    /**
     * 修改用户资料，可同时改角色。
     *
     * <p>姓名传空串时视为"不改"而不是"清空"：姓名为空的账号在页面上会显示成空白，
     * 排查时极难定位是哪个账号，因此不允许被清空。
     *
     * @param id  用户 id
     * @param dto 待修改字段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveDTO dto) {
        SysUser existing = requireUser(id);
        if (dto == null) {
            return;
        }

        SysUser patch = new SysUser();
        patch.setId(existing.getId());
        if (hasText(dto.getName())) {
            patch.setRealName(dto.getName().trim());
        }
        // 手机号与邮箱允许清空，因此直接赋值（空串归一化成 null）
        patch.setPhone(trimToNull(dto.getPhone()));
        patch.setEmail(trimToNull(dto.getEmail()));
        // password 字段在这里被显式忽略：修改资料不该顺带改口令。
        // 改口令只有两个独立入口 —— 本人改密 PUT /api/v1/auth/password（要校验旧口令，
        // 只踢其它会话）与管理员重置 PUT /api/v1/system/users/{id}/password（踢全部会话）。
        // 在编辑资料里顺手写库，会把这两个入口该有的旧口令校验与会话处理全部绕过去。
        userMapper.updateById(patch);

        if (hasText(dto.getRole())) {
            SysRole role = requireRole(dto.getRole());
            SysRole current = roleResolver.byUserId(existing.getId());
            if (current == null || !Objects.equals(current.getId(), role.getId())) {
                roleResolver.bind(existing.getId(), role.getId());
            }
        }
    }

    /**
     * 启用 / 停用账号。
     *
     * @param id  用户 id
     * @param dto 目标状态
     */
    @Override
    public void updateStatus(Long id, StatusUpdateDTO dto) {
        SysUser existing = requireUser(id);
        UserStatusEnum status = dto == null ? null : UserStatusEnum.of(dto.getStatus());
        if (status == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态值不合法");
        }
        // 自锁防护：停用自己之后就再也登录不进来，只能去数据库改回 1。
        if (UserStatusEnum.DISABLED == status && Objects.equals(existing.getId(), AuthUtils.getUserId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不能停用当前登录的账号");
        }

        SysUser patch = new SysUser();
        patch.setId(existing.getId());
        patch.setStatus(status.getDbValue());
        userMapper.updateById(patch);
    }

    /**
     * 管理员重置指定用户的口令，并踢掉该账号全部会话。
     *
     * <p>与本人改密（{@code PUT /api/v1/auth/password}）是两套口径：本人改密要校验旧口令，
     * 且只踢其它会话、保留当前会话（改完还得继续用）；管理员重置不需要也无法校验旧口令，
     * 因此<b>必须踢掉该账号全部会话</b> —— 重置的典型场景就是口令外泄，旧 token 还能用
     * 等于没重置。会话按登录 id 集中存储，一次 {@code StpUtil.logout(id)} 即清掉该账号所有端。
     *
     * @param id  用户 id
     * @param dto 新口令，留空则重置为默认口令
     * @throws BusinessException 用户不存在（10002）、口令不符合策略（10001）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordDTO dto) {
        SysUser existing = requireUser(id);

        // 留空按"重置为默认口令"处理而不是报错：管理员代学生找回账号时前端本就允许不填，
        // 报错只会逼管理员自己编一个口令再口头转达。
        String rawPassword = (dto != null && hasText(dto.getPassword())) ? dto.getPassword() : DEFAULT_PASSWORD;
        String policyError = PasswordUtils.checkPolicy(rawPassword);
        if (policyError != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, policyError);
        }

        // 只 patch password 一个字段：updateById 只写非 null 列，
        // 其余列留 null 就不会被回写，避免覆盖掉并发修改的资料。
        SysUser patch = new SysUser();
        patch.setId(existing.getId());
        patch.setPassword(PasswordUtils.encode(rawPassword));
        userMapper.updateById(patch);

        // 放在写库之后：踢会话若抛异常，事务回滚，口令也一并撤销，不会出现"改了密码但旧会话还活着"。
        StpUtil.logout(existing.getId());
    }

    /**
     * 删除用户（逻辑删除）并级联清掉该账号的全部业务数据。
     *
     * <p>{@code sys_user_role} 按物理删除设计（无 deleted 列），这里显式清掉关联，
     * 避免留下一批指向已删用户的孤儿行 —— 它们会让"按角色筛用户"的 IN 集合
     * 白查一批 id，也会让角色人数统计的口径越来越依赖 {@code u.deleted = 0} 这个补丁。
     *
     * <p><b>学生档案与业务数据由 {@link StudentArchivePurger} 级联处理</b>（待办 B31）：
     * 此前本方法只置 {@code sys_user.deleted = 1}，该账号的 {@code student_info}、
     * 报名、签到、服务时长全部留在库里并<b>继续计入看板</b>（实测跑一轮端到端脚本，
     * 看板就从 1503 学生 / 10719 报名 / 19319.3 小时漂到 1504 / 10720 / 19321.8）。
     * 三张表分属三个模块，跨模块动作走两个端口，本方法只负责把它们与删账号
     * 放进同一个事务：任何一步失败都整体回滚，不会留下"删了一半"的账号。
     *
     * @param id 用户 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser existing = requireUser(id);
        if (Objects.equals(existing.getId(), AuthUtils.getUserId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不能删除当前登录的账号");
        }
        userMapper.deleteById(existing.getId());
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, existing.getId()));
        // 放在账号删除之后：销档要按 user_id 反查档案，而它读的是 student_info，与账号状态无关，
        // 顺序上两者等价；放在最后是为了让"账号已删"这件事在日志里先出现，
        // 出问题时一眼能看出是哪一步没走完（回滚后库里两者都还在）。
        studentArchivePurger.purgeByUserId(existing.getId());
    }

    /**
     * 实体转响应对象。
     *
     * <p><b>密码在这里被丢掉</b>：本方法是 {@code SysUser} 唯一的出口，
     * 新增字段时记得同步补进 {@link UserVO}，不要图省事直接把实体返回前端。
     *
     * @param user 用户实体
     * @param role 角色，允许为 null（用户没有关联角色时）
     * @return 响应对象
     */
    private UserVO toVO(SysUser user, SysRole role) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getRealName());
        vo.setRole(role == null ? null : role.getRoleCode());
        vo.setRoleLabel(role == null ? null : role.getRoleName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        UserStatusEnum status = UserStatusEnum.ofDbValue(user.getStatus());
        vo.setStatus(status == null ? null : status.getCode());
        // 格式化成字符串再返回：页面直接渲染 {{ row.lastLoginAt }}，
        // 返回 LocalDateTime 会带上 ISO 的 T，与其它页面的时间格式不一致。
        vo.setLastLoginAt(DateTimeUtils.formatDateTime(user.getLastLoginAt()));
        return vo;
    }

    /**
     * 取用户，取不到直接抛业务异常。
     *
     * @param id 用户 id
     * @return 用户实体
     * @throws BusinessException 用户不存在或已删除（10002）
     */
    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 按角色码取角色，取不到直接抛业务异常。
     *
     * <p>这里必须报错而不是"角色为空也能建"：没有角色的账号登录后角色码为 null，
     * 任何 {@code @SaCheckPermission} 都会拒绝，表现为"能登录但什么都打不开"。
     *
     * @param roleCode 角色码，缺省按 STUDENT 处理
     * @return 角色实体
     * @throws BusinessException 角色码非法或角色不存在（10001）
     */
    private SysRole requireRole(String roleCode) {
        String code = hasText(roleCode) ? roleCode.trim() : RoleCodeEnum.STUDENT.getCode();
        SysRole role = roleResolver.byCode(code);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "角色不存在");
        }
        return role;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    /**
     * 学号是否已被某个学生档案占用。
     *
     * <p>用 {@code exists} 而不是 {@code selectOne} + 判空：这里只关心"有没有"，
     * 不需要把整行拉回来（student_info 若干列可能很大）。
     *
     * @param studentNo 学号，调用方已保证非空
     * @return 已存在时为 true
     */
    private boolean existsStudentNo(String studentNo) {
        return studentInfoMapper.exists(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getStudentNo, studentNo));
    }

    private List<Long> idsOf(List<SysUser> users) {
        return users == null ? List.of() : users.stream().map(SysUser::getId).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
