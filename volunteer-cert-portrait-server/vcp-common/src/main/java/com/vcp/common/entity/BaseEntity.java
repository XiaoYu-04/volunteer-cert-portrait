package com.vcp.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务实体基类，对应建表脚本里的公共审计字段。
 *
 * <p><b>为什么必须靠应用层填充：</b>PostgreSQL 没有 MySQL 的
 * {@code ON UPDATE CURRENT_TIMESTAMP}，DDL 里的 {@code DEFAULT CURRENT_TIMESTAMP}
 * 只在 INSERT 时生效，UPDATE 不会刷新 update_time。所以这里用
 * {@code @TableField(fill = ...)} 声明，交由 vcp-framework 的 MetaObjectHandler 填充；
 * 若只依赖数据库默认值，"最后修改时间"会永远停在建行时间。
 *
 * <p><b>刻意不含 deleted 字段：</b>逻辑删除列只有业务数据表才有（16 张表里 9 张有），
 * 关联表、流水表、日志表都没有。把 deleted 放在基类里，会让没有该列的表生成错误 SQL，
 * 因此由各实体按需自己声明。
 */
@Data
public class BaseEntity implements Serializable {

    /** 创建时间，INSERT 时由 MetaObjectHandler 填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，INSERT 与 UPDATE 时由 MetaObjectHandler 填充 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}