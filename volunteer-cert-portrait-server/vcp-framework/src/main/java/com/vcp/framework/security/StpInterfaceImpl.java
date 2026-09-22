package com.vcp.framework.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.vcp.common.enums.RoleCodeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Sa-Token 权限数据源：告诉框架「某个账号拥有哪些角色、哪些权限」。
 *
 * <p>Sa-Token 在调用 {@code StpUtil.hasRole(...)} / {@code StpUtil.hasPermission(...)}
 * 时会回调本类。声明为 Spring Bean 即可，框架的 SaBeanInject 会自动把它注册进 SaManager，
 * 无需额外配置。
 *
 * <p><b>为什么权限映射是写死的静态表：</b>后端<b>没有权限表</b>——{@code sys_role} 只有
 * {@code role_code} / {@code role_name} 两列，没有 perms 列，也没有 role-permission 关联表。
 * 本系统的三个角色权限边界固定，因此把映射放在代码里，避免为了形式上的「动态权限」
 * 而多加两张表。
 *
 * <p><b>与前端 {@code volunteer-cert-portrait-web/src/stores/user.js} 的 {@code ROLE_PERMS}
 * 保持同步；后端暂无权限表，改动需两边同时改。</b>前端按钮级显隐用的是同一份映射
 * （{@code v-perm} 指令读 store 的 perms），后端这份用于接口级拦截，两边不一致会出现
 * 「按钮点得到、接口报 20003」或「有接口权限、页面却不给入口」的错位。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 角色码 → 权限标识集合。
     *
     * <p>与前端 stores/user.js 的 ROLE_PERMS 逐条对应：学生 5 条 / 组织管理员 7 条 /
     * 学校管理员 16 条。权限标识统一为「域:资源:操作」，与后端 {@code @SaCheckPermission}
     * 的写法同构。
     */
    private static final Map<String, List<String>> ROLE_PERMS = Map.of(
            RoleCodeEnum.STUDENT.getCode(), List.of(
                    "volunteer:activity:list",
                    "volunteer:signup:create",
                    "volunteer:signup:cancel",
                    "certification:duration:mine",
                    "portrait:profile:mine"
            ),
            RoleCodeEnum.ORG_ADMIN.getCode(), List.of(
                    "volunteer:activity:list",
                    "volunteer:activity:create",
                    "volunteer:activity:update",
                    "volunteer:activity:publish",
                    "volunteer:signup:audit",
                    "volunteer:attendance:manage",
                    "certification:duration:submit"
            ),
            RoleCodeEnum.SCHOOL_ADMIN.getCode(), List.of(
                    "volunteer:activity:list",
                    "volunteer:activity:update",
                    "volunteer:signup:audit",
                    "volunteer:attendance:manage",
                    "volunteer:category:manage",
                    "org:info:audit",
                    "certification:duration:approve",
                    "portrait:profile:list",
                    "system:user:list",
                    "system:user:create",
                    "system:user:update",
                    "system:user:delete",
                    "system:role:list",
                    "system:notice:manage",
                    "system:log:list",
                    "analytics:dashboard:view"
            )
    );

    /**
     * 返回指定账号拥有的权限码集合。
     *
     * <p>权限完全由角色决定，因此先从会话里取角色码再查静态表；取不到角色（会话缺失或
     * 角色码未写入）时返回空集合，即不授予任何权限——宁可拒绝也不放行。
     *
     * @param loginId   账号 id，本系统约定为 {@code sys_user.id}
     * @param loginType 账号体系类型，本系统只用默认的 {@code login}
     * @return 权限码集合，无权限时为空集合而非 null
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String roleCode = getRoleCode(loginId);
        if (roleCode == null) {
            return List.of();
        }
        return ROLE_PERMS.getOrDefault(roleCode, List.of());
    }

    /**
     * 返回指定账号拥有的角色标识集合。
     *
     * <p>本系统一个账号只对应一个角色（{@code sys_user.role_id} 是单值），故最多返回一个元素。
     *
     * @param loginId   账号 id，本系统约定为 {@code sys_user.id}
     * @param loginType 账号体系类型，本系统只用默认的 {@code login}
     * @return 角色码集合，无角色时为空集合而非 null
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String roleCode = getRoleCode(loginId);
        return roleCode == null ? List.of() : List.of(roleCode);
    }

    /**
     * 从账号会话中读取角色码。
     *
     * <p>刻意不走 {@code StpUtil.getRoleList(loginId)}：那条路径会回调本类的
     * {@link #getRoleList}，形成无限递归。角色码在登录成功时由登录接口写入会话，
     * 键名见 {@link AuthUtils#SESSION_KEY_ROLE_CODE}。
     *
     * @param loginId 账号 id
     * @return 角色码；会话不存在或未写入角色码时返回 null
     */
    private String getRoleCode(Object loginId) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return null;
        }
        Object roleCode = session.get(AuthUtils.SESSION_KEY_ROLE_CODE);
        return roleCode == null ? null : roleCode.toString();
    }
}