package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.system.entity.SysRole;
import com.vcp.system.entity.SysUserRole;
import com.vcp.system.mapper.SysRoleMapper;
import com.vcp.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户 ↔ 角色解析器：把 {@code sys_user_role} 这层关联收敛到一处。
 *
 * <p>登录要按用户取角色码，用户列表要按角色码筛人、按用户补角色列，
 * 日志要按用户推角色名 —— 四处都在做同一件事。抽出来是为了避免
 * 「关联表怎么查」这个知识散落在多个 Service 里各写一遍而慢慢跑偏。
 *
 * <p>本类只做查询与绑定，不校验权限、不抛业务异常，由调用方决定语义。
 */
@Component
@RequiredArgsConstructor
public class RoleResolver {

    private final SysRoleMapper roleMapper;

    private final SysUserRoleMapper userRoleMapper;

    /**
     * 按角色码取角色。
     *
     * @param roleCode 角色码，如 STUDENT
     * @return 角色；不存在或已被逻辑删除时返回 null
     */
    public SysRole byCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .last("LIMIT 1"));
    }

    /**
     * 取单个用户的角色。
     *
     * @param userId 用户 id
     * @return 角色；该用户没有关联角色时返回 null
     */
    public SysRole byUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return byUserIds(List.of(userId)).get(userId);
    }

    /**
     * 批量取多个用户的角色，用于列表页一次性补齐角色列。
     *
     * <p>分两步查（先查关联再查角色）而不是一次 JOIN：JOIN 的结果要拆回
     * Map 反而更绕，且两步查询能吃到主键索引，列表页每页最多几十条，
     * 两次往返完全够用。用批量而不是循环单查，是为了避免 N+1。
     *
     * @param userIds 用户 id 集合
     * @return 用户 id → 角色；没有角色的用户不会出现在 Map 里
     */
    public Map<Long, SysRole> byUserIds(Collection<Long> userIds) {
        Map<Long, SysRole> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        List<SysUserRole> links = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .in(SysUserRole::getUserId, userIds));
        if (links.isEmpty()) {
            return result;
        }

        Set<Long> roleIds = new HashSet<>();
        for (SysUserRole link : links) {
            if (link.getRoleId() != null) {
                roleIds.add(link.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return result;
        }

        Map<Long, SysRole> roleById = new HashMap<>();
        for (SysRole role : roleMapper.selectBatchIds(roleIds)) {
            roleById.put(role.getId(), role);
        }

        // 一个用户理论上只有一条关联，真出现多条时以先查到的那条为准，不抛异常 ——
        // 列表页不该因为一条脏数据整体打不开。
        for (SysUserRole link : links) {
            SysRole role = roleById.get(link.getRoleId());
            if (role != null) {
                result.putIfAbsent(link.getUserId(), role);
            }
        }
        return result;
    }

    /**
     * 给用户绑定角色。
     *
     * <p>先删旧关联再插新的，保证一个用户只有一条关联 ——
     * {@code sys_user_role} 上有 {@code uk_user_role(user_id, role_id)} 唯一约束，
     * 但那是「同一对不重复」，换个角色插进去会变成两条，必须自己先清理。
     *
     * @param userId 用户 id
     * @param roleId 角色 id
     */
    public void bind(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return;
        }
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        SysUserRole link = new SysUserRole();
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleMapper.insert(link);
    }
}
