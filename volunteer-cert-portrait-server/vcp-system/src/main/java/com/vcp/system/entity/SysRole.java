package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体，对应 sys_role 表。
 *
 * <p>系统采用三角色固定权限模型，角色不可新增或删除，因此本表只有种子数据。
 * 注意本表没有 perms 列 —— 权限映射写在
 * com.vcp.framework.security.StpInterfaceImpl 的静态表里，原因见该类注释。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：STUDENT / ORG_ADMIN / SCHOOL_ADMIN，见 RoleCodeEnum */
    private String roleCode;

    /** 角色名称，与前端展示的中文一致 */
    private String roleName;

    private String remark;

    @TableLogic
    private Integer deleted;
}
