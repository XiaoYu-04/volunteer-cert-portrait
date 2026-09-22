package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典实体，对应 sys_dict 表。
 *
 * <p>职责是「英文码 -> 中文标签 + 色调」的翻译层。前端 stores/dict.js 的
 * load() 会用本表数据整体替换本地静态字典，因此 tone 必须返回，
 * 否则所有状态标签的颜色都会退化成灰色（前端写的是 item.tone || 'mute'）。
 * tone 列由 sql/05_backend_gap_fix.sql 补齐。
 *
 * <p>本表没有 deleted 列（字典项用 status 停用而非删除），
 * 所以本类虽然继承 BaseEntity，但不含逻辑删除字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型：activity_status / signup_status / user_status 等 */
    private String dictType;

    /** 字典键：入库的英文枚举码 */
    private String dictKey;

    /** 字典值：前端展示的中文 */
    private String dictValue;

    /** 标签色调：mute / ok / warn / bad / info */
    private String tone;

    /** 同类型内的排序 */
    private Integer sort;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
