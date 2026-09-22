package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.framework.security.StpInterfaceImpl;
import com.vcp.system.entity.SysRole;
import com.vcp.system.mapper.SysRoleMapper;
import com.vcp.system.mapper.SysUserRoleMapper;
import com.vcp.system.service.RoleService;
import com.vcp.system.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色服务实现。
 *
 * <p>角色数量固定为 3，因此逐条查人数完全够用，不做批量优化 ——
 * 为 3 条数据引入一次额外的分组查询，换来的复杂度比省下的两次往返更贵。
 *
 * <p>权限清单取自 {@link StpInterfaceImpl} 的静态映射而不是数据库：库里没有
 * 权限表（原因见该类的注释），角色管理页要展示的权限必须与接口实际拦截用的是
 * 同一份数据，否则会出现"页面说有权限、接口却报 20003"的错位。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;

    private final SysUserRoleMapper userRoleMapper;

    /**
     * 取全部角色。
     *
     * @return 角色列表
     */
    @Override
    public List<RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));

        return roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            // 前端的字段名是 code，不是 roleCode，这里必须映射
            vo.setCode(role.getRoleCode());
            vo.setName(role.getRoleName());
            vo.setRemark(role.getRemark());
            Long count = userRoleMapper.countUsersByRoleId(role.getId());
            vo.setUserCount(count == null ? 0L : count);
            vo.setPerms(StpInterfaceImpl.getPermsByRoleCode(role.getRoleCode()));
            return vo;
        }).toList();
    }
}
