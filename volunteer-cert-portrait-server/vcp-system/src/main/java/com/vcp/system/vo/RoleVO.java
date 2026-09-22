package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色响应对象，供角色管理页与用户管理页的角色下拉共用。
 *
 * <p>字段与前端 roles 数据逐条对应：code / name / userCount / remark / perms。
 *
 * <p><b>perms 不在数据库里</b>：sys_role 没有 perms 列，也没有角色-权限关联表，
 * 权限映射写在 com.vcp.framework.security.StpInterfaceImpl 的静态表里。
 * 本类的 perms 就是从那里读出来的，因此角色管理页显示的权限清单
 * 与接口实际拦截用的是同一份数据，不会出现"页面说有权限、接口却报 20003"。
 *
 * <p>注意 code 用的是前端的字段名（对应实体的 roleCode），不要写成 roleCode。
 */
@Data
public class RoleVO implements Serializable {

    private Long id;

    /** 角色码，对应 sys_role.role_code */
    private String code;

    /** 角色名称，对应 sys_role.role_name */
    private String name;

    /** 该角色下的账号数（已排除逻辑删除的用户） */
    private Long userCount;

    private String remark;

    /** 权限标识清单，来自 StpInterfaceImpl 的静态映射 */
    private List<String> perms;
}
