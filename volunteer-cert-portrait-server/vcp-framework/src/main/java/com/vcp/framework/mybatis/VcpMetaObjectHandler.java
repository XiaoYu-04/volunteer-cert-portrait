package com.vcp.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充器：负责 create_time / update_time。
 *
 * <p><b>这个类在堵一个具体的坑：</b>PostgreSQL 没有 MySQL 的
 * {@code ON UPDATE CURRENT_TIMESTAMP}，建表脚本里的 {@code DEFAULT CURRENT_TIMESTAMP}
 * 只在 INSERT 时生效，UPDATE 时不会自动刷新 update_time；本库也没有任何
 * {@code BEFORE UPDATE} 触发器兜底。不靠这个填充器，"最后修改时间"会永远停在建行时间，
 * 审核、流转等场景的时间线全部失真。此条审计发现记录在 {@code docs/待办清单.md} 的 B17。
 *
 * <p><b>为什么用 strictInsertFill / strictUpdateFill：</b>这两个方法只在实体字段确实声明了
 * 对应 fill 策略时才写入，不会给没有该字段的表硬塞值。策略本身声明在
 * {@code com.vcp.common.entity.BaseEntity} 上——createTime 为 {@code FieldFill.INSERT}、
 * updateTime 为 {@code FieldFill.INSERT_UPDATE}，因此各业务实体只要继承 BaseEntity 即可生效，
 * 无需重复声明。
 *
 * <p>时间类型统一用 {@code LocalDateTime}，与实体字段类型保持一致；
 * 本库时间列为不带时区的 {@code TIMESTAMP}，不要换成 {@code java.util.Date}。
 */
@Component
public class VcpMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充：创建时间与更新时间都取当前时间。
     *
     * @param metaObject 待填充的实体元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新时填充：只刷新更新时间，创建时间保持不变。
     *
     * @param metaObject 待填充的实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}