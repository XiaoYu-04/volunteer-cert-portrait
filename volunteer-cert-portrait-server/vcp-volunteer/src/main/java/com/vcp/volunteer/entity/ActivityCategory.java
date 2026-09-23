package com.vcp.volunteer.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    /**
     * 说明。列由 {@code sql/07_demo_scale.sql} 追加（此前只收不落，见 CategorySaveDTO 的历史注释）。
     *
     * <p>声明 {@code ALWAYS} 是为了让「清空说明」能真正写库：MyBatis-Plus 默认 NOT_NULL 策略下
     * {@code updateById} 会跳过 null 字段，用户清空 textarea 后旧值会留在库里。
     * 代价是**该字段每次更新都会按传入值覆写**（传 null 即清空），与
     * {@code ServiceDuration.auditRemark} 等同一种取舍。
     * 本实体的 {@code updateById} 只有 {@code ActivityCategoryServiceImpl.updateCategory} 一处调用，
     * 故不存在「别处的更新顺手把说明抹掉」的风险；若将来新增调用点需重新评估。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 排序，升序 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
