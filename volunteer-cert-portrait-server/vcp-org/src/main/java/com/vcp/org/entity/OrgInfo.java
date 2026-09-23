package com.vcp.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 志愿组织实体，对应 org_info 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_info")
public class OrgInfo extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组织管理员账号 id */
    private Long contactUserId;

    private String orgName;

    private String orgType;

    private String contactName;

    private String phone;

    private String email;

    private String description;

    private String status;

    @TableLogic
    private Integer deleted;

    private String code;

    private String college;

    private Integer memberCount;

    private LocalDate foundedAt;

    private String auditRemark;
}
