package com.vcp.system.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.RoleCodeEnum;
import com.vcp.common.enums.UserStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.framework.security.AuthUtils;
import com.vcp.system.dto.LoginDTO;
import com.vcp.system.dto.ProfileUpdateDTO;
import com.vcp.system.dto.RegisterDTO;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.entity.SysRole;
import com.vcp.system.entity.SysUser;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.service.AuthService;
import com.vcp.system.service.OrgLookupPort;
import com.vcp.system.vo.LoginVO;
import com.vcp.system.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 认证服务实现。
 *
 * <p>实现上要紧的三件事：
 * <ol>
 *   <li><b>登录态由 Sa-Token 的会话承载</b>，而权限判定所需的角色码、orgId、
 *       studentId 由本类在登录时写进会话。这三个键的读取方是
 *       {@link AuthUtils}，键名必须用它提供的常量，手写字符串一旦写错
 *       不会报错、只会表现为「能登录但查不到自己的数据」。</li>
 *   <li><b>密码当前是明文比对</b>（待办 B15）。接入加密时只需改本类的
 *       校验与写入两处，其它代码不用动。</li>
 *   <li><b>会话里不存密码、手机号、邮箱</b>。会话对象会被前端存进 localStorage，
 *       只放页面渲染必需的字段。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 用户名规则，与前端 RegisterView 的校验一致 */
    private static final Pattern RE_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    /** 手机号规则 */
    private static final Pattern RE_PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱规则 */
    private static final Pattern RE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 密码最短长度 */
    private static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * 会话中存放用户名的键。
     *
     * <p>写入它能让操作日志切面省掉一次按 userId 反查用户名的查询
     * （切面里的同名常量见 OperationLogAspect）。切面读取失败也只是回退成
     * 由落库监听器回填，所以这里是纯优化，写不写都不影响正确性。
     */
    private static final String SESSION_KEY_USERNAME = "username";

    private final SysUserMapper userMapper;

    private final StudentInfoMapper studentInfoMapper;

    private final RoleResolver roleResolver;

    /**
     * 组织信息查询端口。
     *
     * <p>用 ObjectProvider 而不是直接注入：vcp-org 还没实现该端口（待办 B7），
     * 直接注入会因为找不到 Bean 而启动失败。取不到时安静跳过，
     * 组织管理员的 orgId 留空，登录本身不受影响。
     */
    private final ObjectProvider<OrgLookupPort> orgLookupPortProvider;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = findByUsername(dto.getUsername());

        // 用户不存在与密码错误返回同一个提示，避免被用来枚举系统里有哪些账号
        if (user == null || !Objects.equals(user.getPassword(), dto.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED, "用户名或密码错误");
        }

        // 停用判定放在密码校验之后：先确认对方确实持有正确口令，
        // 再告知账号被停用，否则等于向未通过认证的人泄露了账号存在且被停用
        if (!Objects.equals(UserStatusEnum.ACTIVE.getDbValue(), user.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ACCOUNT_DISABLED);
        }

        SysRole role = roleResolver.byUserId(user.getId());
        if (role == null) {
            // 没有角色的账号无法判定权限，放进来会一路 20003，不如在入口说清楚
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "该账号未分配角色，请联系学校管理员");
        }

        StudentInfo student = isStudent(role) ? findStudentByUserId(user.getId()) : null;
        Long orgId = isOrgAdmin(role) ? findOrgId(user.getId()) : null;

        establishSession(user, role, orgId, student == null ? null : student.getId());
        recordLoginTime(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(buildSession(user, role, orgId, student));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // 客户端校验只是体验优化，不能当成数据守门人 —— 前端 mock 层也是这么做的
        if (!RE_USERNAME.matcher(nullToEmpty(dto.getUsername())).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名为 4-20 位字母、数字或下划线");
        }
        if (nullToEmpty(dto.getName()).isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "姓名不能为空");
        }
        if (nullToEmpty(dto.getPassword()).length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "密码至少 6 位");
        }
        if (!RE_PHONE.matcher(nullToEmpty(dto.getPhone())).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请输入 11 位手机号");
        }
        if (!RE_EMAIL.matcher(nullToEmpty(dto.getEmail())).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请输入有效的邮箱地址");
        }
        if (findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名已存在");
        }
        if (existsPhone(dto.getPhone())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该手机号已被注册");
        }

        SysRole studentRole = roleResolver.byCode(RoleCodeEnum.STUDENT.getCode());
        if (studentRole == null) {
            // 角色种子数据缺失属于部署问题，不是用户输入问题，故用系统错误码
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "学生角色缺失，请联系管理员初始化基础数据");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setRealName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setStatus(UserStatusEnum.ACTIVE.getDbValue());
        userMapper.insert(user);

        roleResolver.bind(user.getId(), studentRole.getId());

        // 注册完直接登录，与前端 mock 一致（前端拿到 token 就写入登录态）
        establishSession(user, studentRole, null, null);

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(buildSession(user, studentRole, null, null));
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public SessionVO currentUser() {
        Long userId = AuthUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // 用户被删掉但 token 还没过期：按登录失效处理，前端会清 token 回登录页
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }
        SysRole role = roleResolver.byUserId(userId);
        StudentInfo student = isStudent(role) ? findStudentByUserId(userId) : null;
        Long orgId = isOrgAdmin(role) ? findOrgId(userId) : null;
        return buildSession(user, role, orgId, student);
    }

    @Override
    public SessionVO updateProfile(ProfileUpdateDTO dto) {
        Long userId = AuthUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }

        // 只覆盖有值的字段：前端在"取消编辑"时可能提交空串，
        // 直接把空串写进去会把已有资料清空
        if (hasText(dto.getName())) {
            user.setRealName(dto.getName().trim());
        }
        if (hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone().trim());
        }
        if (hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail().trim());
        }
        userMapper.updateById(user);

        SysRole role = roleResolver.byUserId(userId);
        StudentInfo student = isStudent(role) ? findStudentByUserId(userId) : null;
        Long orgId = isOrgAdmin(role) ? findOrgId(userId) : null;
        return buildSession(user, role, orgId, student);
    }

    /**
     * 建立登录态：Sa-Token 登录 + 往会话写入权限判定所需的三个键。
     *
     * @param user      用户
     * @param role      角色，允许为 null（此时不写角色码，权限判定会一律拒绝）
     * @param orgId     所属组织 id，非组织管理员传 null（此时不写该键）
     * @param studentId 学生档案 id，非学生传 null（此时不写该键）
     */
    private void establishSession(SysUser user, SysRole role, Long orgId, Long studentId) {
        StpUtil.login(user.getId());
        SaSession session = StpUtil.getSession();
        // 值为 null 的键一律不写，而不是写成 null：Sa-Token 的 SaSession.set 不接受 null 值，
        // 直接抛 NullPointerException。踩过一次——学校管理员与学生登录时报"系统繁忙"，
        // 因为这两类账号的 orgId 本来就是空的；组织管理员的 orgId 非空，反而能正常登录，
        // 表现为"只有部分账号登不进去"，很容易误判成数据问题。
        // 不写键与写 null 对读取方完全等价：AuthUtils 取不到键时本来就返回 null。
        setIfNotNull(session, AuthUtils.SESSION_KEY_ROLE_CODE, role == null ? null : role.getRoleCode());
        setIfNotNull(session, AuthUtils.SESSION_KEY_ORG_ID, orgId);
        setIfNotNull(session, AuthUtils.SESSION_KEY_STUDENT_ID, studentId);
        setIfNotNull(session, SESSION_KEY_USERNAME, user.getUsername());
    }

    /**
     * 值非 null 时才写入会话。
     *
     * @param session 会话
     * @param key     会话键名
     * @param value   值，为 null 时跳过
     */
    private void setIfNotNull(SaSession session, String key, Object value) {
        if (value != null) {
            session.set(key, value);
        }
    }

    /**
     * 记录最近登录时间。
     *
     * <p>刻意不让它影响登录：写失败只记 warn。用户管理页少一个时间不算问题，
     * 但因此登录不了是问题。
     *
     * @param userId 用户 id
     */
    private void recordLoginTime(Long userId) {
        try {
            SysUser patch = new SysUser();
            patch.setId(userId);
            patch.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(patch);
        } catch (Exception e) {
            log.warn("[登录] 记录最近登录时间失败，已忽略。userId={}", userId, e);
        }
    }

    /**
     * 组装会话对象。
     *
     * @param user    用户
     * @param role    角色，允许为 null
     * @param orgId   组织 id
     * @param student 学生档案，非学生传 null
     * @return 会话对象
     */
    private SessionVO buildSession(SysUser user, SysRole role, Long orgId, StudentInfo student) {
        SessionVO vo = new SessionVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getRealName());
        vo.setRole(role == null ? null : role.getRoleCode());
        vo.setRoleLabel(role == null ? null : role.getRoleName());
        vo.setOrgId(orgId);
        vo.setStudentId(student == null ? null : student.getId());
        vo.setCollege(student == null ? null : student.getCollege());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setAvatarText(sealText(user.getRealName()));
        return vo;
    }

    /**
     * 取姓名末两字作印章文字，与前端 utils/format.js 的 sealText 保持一致。
     *
     * <p>放在后端算是因为会话对象里前端直接用 avatarText 渲染印章，
     * 不再自己截取；两边算法必须一致，改一处要同时改另一处。
     *
     * @param name 姓名，允许为 null
     * @return 印章文字；姓名为空时返回「志愿」
     */
    private String sealText(String name) {
        if (name == null || name.isBlank()) {
            return "志愿";
        }
        String trimmed = name.trim();
        return trimmed.length() <= 2 ? trimmed : trimmed.substring(trimmed.length() - 2);
    }

    private SysUser findByUsername(String username) {
        if (!hasText(username)) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username.trim())
                .last("LIMIT 1"));
    }

    private boolean existsPhone(String phone) {
        if (!hasText(phone)) {
            return false;
        }
        return userMapper.exists(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone.trim()));
    }

    private StudentInfo findStudentByUserId(Long userId) {
        return studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId)
                .last("LIMIT 1"));
    }

    /**
     * 取组织管理员负责的组织 id。
     *
     * <p>端口没有实现类时（vcp-org 未开工）返回 null，不抛异常 ——
     * 缺 orgId 只影响组织端页面的数据范围，不该让登录整体失败。
     *
     * @param userId 用户 id
     * @return 组织 id，取不到时返回 null
     */
    private Long findOrgId(Long userId) {
        OrgLookupPort port = orgLookupPortProvider.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.findOrgIdByUserId(userId);
        } catch (Exception e) {
            log.warn("[登录] 查询所属组织失败，orgId 留空。userId={}", userId, e);
            return null;
        }
    }

    private boolean isStudent(SysRole role) {
        return role != null && RoleCodeEnum.STUDENT.getCode().equals(role.getRoleCode());
    }

    private boolean isOrgAdmin(SysRole role) {
        return role != null && RoleCodeEnum.ORG_ADMIN.getCode().equals(role.getRoleCode());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
