package com.vcp.framework.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.vcp.common.enums.RoleCodeEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;

/**
 * 当前登录用户工具类：业务代码取登录态的唯一入口。
 *
 * <p>业务代码不直接调 {@link StpUtil}，好处有二：一是异常语义统一——Sa-Token 未登录时抛
 * {@code NotLoginException}，本类统一转成业务异常 {@link ErrorCodeEnum#AUTH_FAILED}，
 * 全局异常处理器只需认识一种；二是登录态里存了什么、键名是什么，只在本类与登录接口之间约定，
 * 不散落到各个 Service 里。
 *
 * <h3>登录约定（写登录接口的人请遵守）</h3>
 * <ul>
 *   <li><b>Sa-Token 的 loginId 存用户 id</b>，即 {@code sys_user.id}，用
 *       {@code StpUtil.login(userId)} 写入。</li>
 *   <li><b>登录成功时把 roleCode、orgId、studentId 写入 Session</b>，键名用本类的
 *       {@link #SESSION_KEY_ROLE_CODE} / {@link #SESSION_KEY_ORG_ID} /
 *       {@link #SESSION_KEY_STUDENT_ID} 常量，不要手写字符串字面量：
 *       <pre>{@code
 *       StpUtil.login(userId);
 *       SaSession session = StpUtil.getSession();
 *       session.set(AuthUtils.SESSION_KEY_ROLE_CODE, roleCode);
 *       session.set(AuthUtils.SESSION_KEY_ORG_ID, orgId);        // 学生为 null
 *       session.set(AuthUtils.SESSION_KEY_STUDENT_ID, studentId); // 组织/学校管理员为 null
 *       }</pre></li>
 * </ul>
 * <p>角色码写入会话后，{@link StpInterfaceImpl} 才能据此给出角色与权限列表，两者是配套的。
 *
 * <p>本类所有方法都<b>要求已登录</b>：全局拦截器 {@link SaTokenConfig} 已覆盖除放行清单外的
 * 全部接口，业务代码能被执行就说明登录态有效。因此未登录时统一抛
 * {@link ErrorCodeEnum#AUTH_FAILED}，而不是返回 null 或 false——静默返回假值会把
 * 「没登录」伪装成「没有权限」或「查不到数据」，排查时看不到真正的原因。
 */
public final class AuthUtils {

    /** 会话键名：角色码，值为 {@link RoleCodeEnum#getCode()} */
    public static final String SESSION_KEY_ROLE_CODE = "roleCode";

    /** 会话键名：所属志愿组织 id，仅组织管理员有值，学生与学校管理员为 null */
    public static final String SESSION_KEY_ORG_ID = "orgId";

    /** 会话键名：学生档案 id，仅学生有值，组织管理员与学校管理员为 null */
    public static final String SESSION_KEY_STUDENT_ID = "studentId";

    /** 工具类不允许实例化 */
    private AuthUtils() {
    }

    /**
     * 取当前登录用户的 id。
     *
     * @return 用户 id，即 {@code sys_user.id}
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    public static Long getUserId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 取当前登录用户的角色码。
     *
     * @return 角色码，取值见 {@link RoleCodeEnum}（STUDENT / ORG_ADMIN / SCHOOL_ADMIN）；
     *         已登录但会话里没有角色码时返回 null
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    public static String getRoleCode() {
        Object roleCode = getSessionAttribute(SESSION_KEY_ROLE_CODE);
        return roleCode == null ? null : roleCode.toString();
    }

    /**
     * 当前登录用户是否为学生。
     *
     * @return 角色码为 STUDENT 时返回 true
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    public static boolean isStudent() {
        return RoleCodeEnum.STUDENT.getCode().equals(getRoleCode());
    }

    /**
     * 当前登录用户是否为组织管理员。
     *
     * @return 角色码为 ORG_ADMIN 时返回 true
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    public static boolean isOrgAdmin() {
        return RoleCodeEnum.ORG_ADMIN.getCode().equals(getRoleCode());
    }

    /**
     * 当前登录用户是否为学校管理员。
     *
     * @return 角色码为 SCHOOL_ADMIN 时返回 true
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    public static boolean isSchoolAdmin() {
        return RoleCodeEnum.SCHOOL_ADMIN.getCode().equals(getRoleCode());
    }

    /**
     * 读取当前登录用户会话里的属性。
     *
     * <p>会话在登录时由 {@code StpUtil.login} 创建，这里用 {@code isCreate=false} 读取，
     * 不去凭空建会话。
     *
     * @param key 会话键名，取本类的 SESSION_KEY_* 常量
     * @return 属性值；会话不存在或该键无值时返回 null
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCodeEnum#AUTH_FAILED}
     */
    private static Object getSessionAttribute(String key) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCodeEnum.AUTH_FAILED);
        }
        SaSession session = StpUtil.getSession(false);
        return session == null ? null : session.get(key);
    }
}