package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知 / 公告响应对象。
 *
 * <p>字段名与前端通知列表页、详情页逐条对应：
 * id / title / date / from / top / type / read / content。
 * 页面是<b>直接渲染</b>这些值的（如 {{ row.date }}、{{ notice.from }}），
 * 所以 date 必须是已格式化的 "yyyy-MM-dd" 字符串，不能是 ISO 时间串。
 *
 * <p>两个字段名与实体不同，是刻意做的映射：
 * from 对应实体的 source（from 是 SQL 关键字，列名用不了），
 * read 对应实体的 isRead。
 */
@Data
public class NotificationVO implements Serializable {

    private Long id;

    private String title;

    /** 发布日期，已格式化为 yyyy-MM-dd */
    private String date;

    /** 发布方，取自 notification.source */
    private String from;

    /** 是否置顶 */
    private Boolean top;

    /** 通知类型：SIGNUP / DURATION / SYSTEM */
    private String type;

    /** 是否已读 */
    private Boolean read;

    /** 正文，列表页不需要，详情页使用 */
    private String content;
}
