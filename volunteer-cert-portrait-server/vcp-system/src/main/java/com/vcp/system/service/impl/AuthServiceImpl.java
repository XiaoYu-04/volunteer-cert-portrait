package com.vcp.system.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.RoleCodeEnum;
import com.vcp.common.enums.UserStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PasswordUtils;
import com.vcp.system.dto.ChangePasswordDTO;
import com.vcp.system.dto.LoginDTO;
import com.vcp.system.dto.ProfileUpdateDTO;
import com.vcp.system.dto.RegisterDTO;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.entity.SysRole;
import com.vcp.system.entity.SysUser;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.service.AuthService;
import com.vcp.system.service.DictService;
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

import static com.vcp.common.util.StringUtils.hasText;
import static com.vcp.common.util.StringUtils.nullToEmpty;

/**
 * 认证服务实现。
 *
 * <p>实现上要紧的四件事：
 * <ol>
 *   <li><b>登录态由 Sa-Token 的会话承载</b>，而权限判定所需的角色码、orgId、
 *       studentId 由本类在登录时写进会话。这三个键的读取方是
 *       {@link AuthUtils}，键名必须用它提供的常量，手写字符串一旦写错
 *       不会报错、只会表现为「能登录但查不到自己的数据」。</li>
 *   <li><b>口令走 BCrypt 单向哈希</b>（待办 B15 已完成）：校验只经
 *       {@link PasswordUtils#matches}，写入只经 {@link PasswordUtils#encode}，
 *       全工程就这两处。库里若还留着历史明文记录，matches 一律返回 false
 *       （工具类刻意不做明文兜底），表现为「密码没错却登不进去」，
 *       需要执行 {@code sql/08_password_bcrypt.sql} 刷一遍。</li>
 *   <li><b>连续登录失败会锁账号</b>：计数与判定都在 {@link LoginAttemptGuard}，
 *       且锁定检查必须排在查库与口令校验之前 —— 顺序一旦反过来，
 *       「已锁定」与「口令错误」的响应耗时不同，锁定状态本身就成了可探测的旁路信息。
 *       计数 key 是用户原始输入（trim 后），不是解析出来的用户名，理由见 {@link #login}。</li>
 *   <li><b>登录支持「用户名或学号」</b>：请求体字段名仍是 username，由
 *       {@link #findByAccount(String)} 按「是否纯数字」决定查 student_info 还是 sys_user，
 *       纯数字查不到学号时再按用户名兜底。前端与 mock 都无需感知这层。</li>
 *   <li><b>Sa-Token 会话里只写 roleCode / orgId / studentId / username 四个键</b>，
 *       不放联系方式。返回给前端的 {@link com.vcp.system.vo.SessionVO} 另含
 *       phone / email，仅供个人资料页回显本人数据 —— 前端只把 token 写进
 *       localStorage，info 仅存内存（详见 SessionVO 的类注释）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 用户名规则，与前端 RegisterView 的校验一致 */
    private static final Pattern RE_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    /**
     * 学号规则：纯数字 4-20 位，注册采集与登录识别共用同一条。
     *
     * <p>刻意不写死位数：库里现有形态实测三种并存 —— {@code 20230001}（8 位）、
     * {@code 2023100001}（10 位扩量数据）、{@code S000011}（建档生成的占位学号）。
     * 固定位数会把扩量数据判成非法。
     *
     * <p>两处用法不同、别混：注册时它是<b>准入门槛</b>，不匹配就报「学号为 4-20 位数字」；
     * 登录时它只是<b>路由判据</b>，决定查 student_info 还是 sys_user，
     * 绝不据此报格式错（{@code student} 这种含字母的输入走用户名路径完全合法）。
     */
    private static final Pattern RE_STUDENT_NO = Pattern.compile("^[0-9]{4,20}$");

    /** 手机号规则 */
    private static final Pattern RE_PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱规则 */
    private static final Pattern RE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 学院字典的类型码，与 sys_dict 种子数据里的 dict_type 一致 */
    private static final String DICT_TYPE_COLLEGE = "college";

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

    /** 建档入口：注册时给新学生建一行 student_info，会话里的 studentId 就取自它 */
    private final StudentArchiveRegistrar studentArchiveRegistrar;

    private final RoleResolver roleResolver;

    /** 登录失败计数与锁定判定，登录入口的第一道闸 */
    private final LoginAttemptGuard loginAttemptGuard;

    /**
     * 组织信息查询端口。
     *
     * <p>vcp-org 已提供实现（{@code OrgLookupPortImpl}，B7 已落地），完整应用里能正常取到；
     * 仍用 ObjectProvider 而不是直接注入，是为了让实现缺失时（单模块测试、裁剪部署）
     * 安静跳过而不是启动失败。取不到时组织管理员的 orgId 留空，登录本身不受影响。
     */
    private final ObjectProvider<OrgLookupPort> orgLookupPortProvider;

    /** 学院下拉框的数据源，也是注册时校验学院名的唯一依据 */
    private final DictService dictService;

    @Override
    public LoginVO login(LoginDTO dto) {
        String account = dto.getUsername() == null ? null : dto.getUsername().trim();

        // 计数 key 用「用户原始输入 trim 后的值」，不要顺手改成解析出来的用户名：
        // 锁定判定发生在查库之前（也就早于 BCrypt 校验），此刻还不知道这串输入对应哪个账号，
        // 解析本身就要查库。若为了统一 key 把解析提到这里，就等于把查库挪到锁定检查之前 ——
        // 「已锁定」与「口令错误」两条路径的耗时不再一致，锁定状态成了可探测的旁路信息。
        // 何况锁定期间本来就必须拒绝，不能先查库。
        if (loginAttemptGuard.isLocked(account)) {
            throw new BusinessException(ErrorCodeEnum.ACCOUNT_LOCKED, lockedMessage(account));
        }

        SysUser user = findByAccount(account);

        // 用户不存在与密码错误返回同一个提示，避免被用来枚举系统里有哪些账号
        if (user == null || !PasswordUtils.matches(dto.getPassword(), user.getPassword())) {
            // 不存在的用户名同样计数：否则「连续错 5 次会被锁」只对真实账号成立，
            // 拿"锁没锁"一测就知道账号存不存在，上面那句防枚举就白写了
            loginAttemptGuard.recordFailure(account);
            // 这次失败刚好踩到阈值时给的是锁定文案而不是"用户名或密码错误"，
            // 让「账号不存在」与「存在但密码错」两条路径的返回完全一致
            if (loginAttemptGuard.isLocked(account)) {
                throw new BusinessException(ErrorCodeEnum.ACCOUNT_LOCKED, lockedMessage(account));
            }
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

        // 计数清零放在这两条"口令已通过但登不进去"的分支之后：停用与无角色都不算
        // 认证失败，若在口令匹配处就清零，拿一个口令正确的停用账号反复登录
        // 就能把该用户名的失败计数刷掉
        loginAttemptGuard.clear(account);

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
        // 学号：必填 + 纯数字 4-20 位。文案与前端 RegisterView 的 RE_STUDENT_NO 一字不差，
        // 空着不填与格式写错给同一条提示 —— 对用户来说是同一件事（见 RegisterDTO 的注释）
        String studentNo = dto.getStudentNo() == null ? null : dto.getStudentNo().trim();
        if (!RE_STUDENT_NO.matcher(nullToEmpty(studentNo)).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "学号为 4-20 位数字");
        }
        // 口令规则收敛到 PasswordUtils.checkPolicy：注册、改密、管理员新增/重置共用一套，
        // 分散写迟早会在某个入口漏掉一条；它返回的文案可直接展示给用户
        String policyMessage = PasswordUtils.checkPolicy(dto.getPassword());
        if (policyMessage != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, policyMessage);
        }
        if (!RE_PHONE.matcher(nullToEmpty(dto.getPhone())).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请输入 11 位手机号");
        }
        if (!RE_EMAIL.matcher(nullToEmpty(dto.getEmail())).matches()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请输入有效的邮箱地址");
        }
        // 学院：去空格后非空，且必须命中字典（dict_type = 'college'）的启用项。
        // 不接受自由文本 —— 同一学院一旦有第二种写法，「按学院统计」就会把它算成另一个学院。
        // 学院清单不写死在代码里（学院的增删是数据维护动作，写死意味着加一个学院要发版），
        // 判定收敛在 DictService.containsEnabled 一处：管理员新增学生那条路径用的是同一个方法。
        String college = dto.getCollege() == null ? null : dto.getCollege().trim();
        if (!hasText(college) || !dictService.containsEnabled(DICT_TYPE_COLLEGE, college)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择学院");
        }
        if (findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名已存在");
        }
        if (existsPhone(dto.getPhone())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该手机号已被注册");
        }
        // 学号唯一性在插库前先查一次：student_no 列本身有 UNIQUE 约束，但撞约束抛出来的是
        // 数据库异常，前端只会看到「系统繁忙」，用户不知道改哪里。这里给可读文案。
        // 注意本查询自带 @TableLogic 的 deleted = 0，逻辑删除过的档案看不见 —— 那种残留
        // 仍会撞库级唯一约束，属于兜底路径，不能靠它当主提示。
        // 学号是登录凭据之一（登录支持用户名或学号），重复注册会让两个人抢同一个登录名
        if (existsStudentNo(studentNo)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该学号已被注册");
        }

        SysRole studentRole = roleResolver.byCode(RoleCodeEnum.STUDENT.getCode());
        if (studentRole == null) {
            // 角色种子数据缺失属于部署问题，不是用户输入问题，故用系统错误码
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "学生角色缺失，请联系管理员初始化基础数据");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        // 入库的必须是密文：全工程只有 PasswordUtils 一处做哈希，明文不落库
        user.setPassword(PasswordUtils.encode(dto.getPassword()));
        user.setRealName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setStatus(UserStatusEnum.ACTIVE.getDbValue());
        userMapper.insert(user);

        roleResolver.bind(user.getId(), studentRole.getId());

        // 建档必须排在建会话之前：会话里的 studentId 取自这里，建晚了或漏建，
        // 新学生一登录「我的报名 / 我的时长 / 我的画像」就报 10003。
        // 与上面两句同处一个事务（register 上有 @Transactional），建档失败连账号一起回滚，
        // 不会留下「有账号、无档案」的孤儿账号。
        // 学院在这里一并落库：student_info.college 目前只有注册这一条写入入口（缺陷 B30）
        // 学号改由注册表单采集后原样落库，建档器不再生成 S+userId 占位值（旧账号的占位学号不受影响）
        StudentInfo student = studentArchiveRegistrar.ensureArchive(user.getId(), college, studentNo);

        // 注册完直接登录，与前端 mock 一致（前端拿到 token 就写入登录态）
        establishSession(user, studentRole, null, student.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(buildSession(user, studentRole, null, student));
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

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = AuthUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // 账号被删但 token 还没过期：与 currentUser 一样按登录失效处理
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }

        String policyMessage = PasswordUtils.checkPolicy(dto.getNewPassword());
        if (policyMessage != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, policyMessage);
        }
        // 拿提交上来的新旧明文直接比，而不是拿新明文去 matches 库里的密文：
        // 契约就是「新密码不能与原密码相同」，这样比还省掉一次 BCrypt（约 50-100ms）
        if (Objects.equals(dto.getNewPassword(), dto.getOldPassword())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "新密码不能与原密码相同");
        }
        if (!PasswordUtils.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.OLD_PASSWORD_ERROR);
        }

        // 只 patch password 一个字段：SysUser 其它字段留 null，updateById 默认跳过 null，
        // 不会把并发改过的手机号、邮箱覆盖回旧值
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPassword(PasswordUtils.encode(dto.getNewPassword()));
        userMapper.updateById(patch);

        // 改密后踢掉该账号的其它会话、保留当前会话：口令泄露时这一步才是真正的止血，
        // 但把当前这台设备一起踢下线，用户会以为改密失败、甚至以为账号被盗
        String currentToken = StpUtil.getTokenValue();
        for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
            if (!Objects.equals(token, currentToken)) {
                StpUtil.kickoutByTokenValue(token);
            }
        }
    }

    /**
     * 拼锁定提示文案。
     *
     * <p>带上剩余分钟数而不是只给错误码的默认文案：用户看到「请 3 分钟后再试」会等，
     * 看到「请稍后再试」会一直点，反而把锁定时间不断续上。
     *
     * <p>参数是登录计数用的 key，即用户<b>原始输入</b>（trim 后），可能是用户名也可能是学号，
     * 不是解析出来的用户名 —— 与 {@link #login} 里计数所用的 key 必须是同一个，
     * 否则这里会算出 0 分钟。文案里不出现这个值，所以用哪种形式都不影响展示。
     *
     * @param account 登录计数 key：用户原始输入（用户名或学号），已 trim
     * @return 可直接展示的文案
     */
    private String lockedMessage(String account) {
        return "账号已被锁定，请 " + loginAttemptGuard.remainingMinutes(account) + " 分钟后再试";
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
        // 联系方式：四个入口（login/register/currentUser/updateProfile）手里都已有 SysUser，
        // 直接取即可，不需要额外查库
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
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

    /**
     * 按「用户名或学号」定位账号。
     *
     * <p><b>判定顺序</b>：
     * <ol>
     *   <li>输入是<b>纯数字</b>（{@link #RE_STUDENT_NO}）→ 先按 {@code student_info.student_no}
     *       查档案，命中就用它的 user_id 去查 {@code sys_user}。</li>
     *   <li>学号查不到 → <b>再按用户名兜底查一次</b>。用户名规则本来就允许纯数字
     *       （如 {@code 1234}），少了这一步这些老账号会突然登不进去。</li>
     *   <li>输入含字母等非数字字符 → 直接走用户名路径（{@code student}、{@code stu100001}）。</li>
     * </ol>
     *
     * <p><b>两个不查库的细节</b>：学号查不到档案时不再多查一次 {@code sys_user}
     * —— 上面第 2 步的兜底已经覆盖；档案存在但用户被逻辑删除时返回 null
     * （{@code sys_user} 自带 {@code @TableLogic}），表现为「用户名或密码错误」，
     * 与账号不存在完全一致，不额外泄露"这个学号曾经存在"。
     *
     * <p>本方法只用于决定<b>查哪张表</b>，格式是否合法不由它判定：
     * 登录识别不报格式错，{@code student} 这类输入走用户名路径本来就是对的。
     *
     * @param account 用户输入的账号（用户名或学号），已 trim
     * @return 匹配到的用户；查不到返回 null
     */
    private SysUser findByAccount(String account) {
        if (!hasText(account)) {
            return null;
        }
        if (RE_STUDENT_NO.matcher(account).matches()) {
            SysUser byStudentNo = findByStudentNo(account);
            if (byStudentNo != null) {
                return byStudentNo;
            }
        }
        return findByUsername(account);
    }

    /**
     * 按学号查账号：先查 {@code student_info}，再用 user_id 查 {@code sys_user}。
     *
     * <p>查询自带 {@code @TableLogic} 的 {@code deleted = 0}，已逻辑删除的档案查不出来。
     * 这里也不做投影，理由见 CLAUDE.md 踩坑第 18 条（投影全 NULL 时 selectList 会塞 null）。
     *
     * @param studentNo 学号，纯数字
     * @return 该学号对应的用户；学号不存在、档案已删或用户已删时返回 null
     */
    private SysUser findByStudentNo(String studentNo) {
        StudentInfo student = studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getStudentNo, studentNo)
                .last("LIMIT 1"));
        if (student == null || student.getUserId() == null) {
            return null;
        }
        return userMapper.selectById(student.getUserId());
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

    /**
     * 学号是否已被占用。
     *
     * <p>用 {@code exists} 而不是查出整行：这里只关心有无，少传一份档案数据。
     * 查询自带 {@code @TableLogic} 的 {@code deleted = 0}，逻辑删除过的档案不算占用
     * （那种行仍会撞库级唯一约束，是既有的历史遗留问题，不在这里处理）。
     *
     * @param studentNo 学号，调用前已校验为纯数字且非空
     * @return 已存在返回 true
     */
    private boolean existsStudentNo(String studentNo) {
        return studentInfoMapper.exists(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getStudentNo, studentNo));
    }

    private StudentInfo findStudentByUserId(Long userId) {
        return studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId)
                .last("LIMIT 1"));
    }

    /**
     * 取组织管理员负责的组织 id。
     *
     * <p>vcp-org 已提供实现（{@code OrgLookupPortImpl}）；若运行时没有可用的实现
     * （单模块测试、裁剪部署），这里返回 null，不抛异常 —— 缺 orgId 只影响
     * 组织端页面的数据范围，不该让登录整体失败。
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
}
