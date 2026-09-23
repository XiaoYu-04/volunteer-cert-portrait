package com.vcp.volunteer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 活动分类新增 / 修改请求（POST、PUT /api/v1/categories）。
 *
 * <p>{@code remark}（说明）自 {@code sql/07_demo_scale.sql} 补列后**已真正落库**。
 * 历史坑：此前 {@code activity_category} 没有该列，DTO 收下却丢弃，而前端保存后照旧提示
 * 「保存成功」，用户重填的说明刷新即消失 —— 假成功 + 静默丢数据（审计见待办 B20-2）。
 * 改动时务必让 DTO / entity / Mapper XML / toVO 四处同时到位，缺一处就会退回「静默丢数据」。
 */
@Data
public class CategorySaveDTO implements Serializable {

    @NotBlank(message = "请填写分类名称")
    private String name;

    /** 分类编码，如 COMMUNITY；留空时由服务端按名称生成 */
    private String code;

    /** 排序，升序；为空时排到最后 */
    private Integer sort;

    /** 说明；null 表示不修改，空串表示清空 */
    private String remark;
}
