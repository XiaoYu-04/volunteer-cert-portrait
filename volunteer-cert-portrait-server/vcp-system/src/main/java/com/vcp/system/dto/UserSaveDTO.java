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

    /**
     * 学院名，仅「新增用户 + 角色为学生」时使用。
     *
     * <p>取值必须命中 {@code sys_dict} 里 {@code dict_type = 'college'} 的启用项，
     * 由 Service 校验 —— 与注册接口同一条规则，不接受自由文本：同一学院一旦有第二种
     * 写法，「按学院统计」就会把它算成另一个学院。
     *
     * <p>修改用户时忽略本字段：{@code updateUser} 只 patch {@code sys_user}，
     * 学院落在 {@code student_info} 上，改它属于另一条写路径（补录学生档案）。
     */
    private String college;

    /**
     * 学号，仅「新增用户 + 角色为学生」时使用，此时必填。
     *
     * <p>取值必须是 4-20 位纯数字，由 Service 校验 —— 与注册接口同一条规则。
     * 刻意不写死位数：库内现有学号三种形态并存（{@code 20230001} 八位 /
     * {@code 2023100001} 十位 / {@code S000011} 占位），固定位数会把扩量数据判成非法。
     *
     * <p>非学生角色忽略本字段（与 {@code college} 的处理方式一致）：
     * 学校/组织管理员在 {@code student_info} 里没有对应行，建档只在学生分支发生。
     *
     * <p>修改用户时同样忽略：{@code updateUser} 只 patch {@code sys_user}，
     * 学号落在 {@code student_info} 上，改它属于另一条写路径（补录学生档案）。
     */
    private String studentNo;

    /** 初始密码，仅新增时使用；留空则用默认密码 */
    private String password;
}
