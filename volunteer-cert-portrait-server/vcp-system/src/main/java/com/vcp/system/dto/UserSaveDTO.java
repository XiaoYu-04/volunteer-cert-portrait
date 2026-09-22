package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增 / 修改用户请求体，两个接口共用。
 *
 * <p>新增与修改的字段高度重合，只差 username（新增必填、修改不可改）与
 * password（新增可选、修改不涉及），因此合成一个 DTO，由 Service 按场景校验。
 * 分成两个类会让两边的字段列表长期漂移。
 *
 * <p>没有 status 字段：启停用走独立的 PUT /users/{id}/status 接口，
 * 与前端 updateUserStatus 对应。混在保存里会让"编辑资料"顺手把账号启停用了。
 */
@Data
public class UserSaveDTO implements Serializable {

    /** 登录账号，仅新增时使用 */
    private String username;

    private String name;

    /** 角色码：STUDENT / ORG_ADMIN / SCHOOL_ADMIN */
    private String role;

    private String phone;

    private String email;

    /** 初始密码，仅新增时使用；留空则用默认密码 */
    private String password;
}
