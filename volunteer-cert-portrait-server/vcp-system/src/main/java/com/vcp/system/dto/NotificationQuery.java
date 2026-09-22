package com.vcp.system.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知列表查询条件（GET /api/v1/system/notifications）。
 *
 * <p><b>unreadOnly 为什么是 String 而不是 Boolean</b>：前端这个参数有三种形态 ——
 * 空串（"全部通知"页签）、字符串 'true'（学生端"仅看未读"页签）、
 * 布尔 true/false（管理端页签切换）。布尔值经 axios 序列化后同样变成
 * "true"/"false" 文本，所以统一按字符串收下、只认 "true" 最稳，
 * 不必依赖 Spring 对空串到 Boolean 的隐式转换。
 *
 * <p>列表范围由登录态决定，请求体里没有 userId —— 学生只能看到发给自己的通知，
 * 学校管理员看到的是自己那一份（公告按收件人一行存储，见 Notification 实体注释）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {

    /** 关键字，匹配通知标题 */
    private String keyword;

    /** 通知类型：SIGNUP / DURATION / SYSTEM */
    private String type;

    /** 只看未读：取值 "true" 时生效，其余（含空串）一律视为看全部 */
    private String unreadOnly;
}
