package com.vcp.volunteer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动分类实体，对应 activity_category 表。
 *
 * <p><b>status 是 SMALLINT 而不是英文码</b>：这张表沿用原始设计，用「1 启用 / 0 禁用」标志位，
 * 与「状态一律英文大写码」的约定不一致（审计记录见待办 B16）。列类型已定，
 * 本模块不改 DDL，改为在 VO 层翻译成 ACTIVE / DISABLED 再返回 ——
 * 前端 mock 的分类对象用的就是字符串码。
 *
 * <p>{@code code} 列由 {@code sql/06} 补列脚本追加（前端分类管理页要展示分类编码）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_category")
public class ActivityCategory extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 分类编码，如 COMMUNITY */
    private String code;

    /** 排序，升序 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
