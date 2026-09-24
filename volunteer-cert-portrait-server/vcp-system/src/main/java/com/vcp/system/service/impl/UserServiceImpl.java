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
import com.vcp.system.entity.SysRole;
import com.vcp.system.entity.SysUser;
import com.vcp.system.entity.SysUserRole;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.mapper.SysUserRoleMapper;
import com.vcp.system.service.UserService;
import com.vcp.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 *   <li><b>新增用户不建学生档案</b>：表单里没有学号与学院，凭空生成会污染学籍数据。
 *       学生登录后个人数据为空，等管理员补录 —— 与前端 mock 的行为一致。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 新增用户与重置口令共用的默认口令，与前端表单留空时的提示一致 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final RoleResolver roleResolver;

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
     * 删除用户（逻辑删除）并清掉角色关联。
     *
     * <p>{@code sys_user_role} 按物理删除设计（无 deleted 列），这里显式清掉关联，
     * 避免留下一批指向已删用户的孤儿行 —— 它们会让"按角色筛用户"的 IN 集合
     * 白查一批 id，也会让角色人数统计的口径越来越依赖 {@code u.deleted = 0} 这个补丁。
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
