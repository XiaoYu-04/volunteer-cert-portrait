package com.vcp.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化工具。
 *
 * <p><b>为什么把时间格式化成字符串再返回</b>：前端若干页面是把时间字段直接渲染的
 * （日志页的 {{ row.time }}、通知页的 {{ row.date }}），并没有走 formatDate 之类的转换。
 * 若后端返回 LocalDateTime，Jackson 默认会输出 ISO-8601 的 "2025-03-21T09:12:33"，
 * 页面上就会带着那个 T。改成在服务端统一格式化，输出与前端 mock 完全一致的
 * "2025-03-21 09:12:33"，既不用给 Jackson 配全局格式（Boot 4 已换成 Jackson 3，
 * 配置方式与旧版不同，容易踩坑），也不依赖浏览器的非标准日期解析。
 *
 * <p>统一用 {@link LocalDateTime}：库里是 TIMESTAMP（无时区），不要换成 Date。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class DateTimeUtils {

    /** 仅日期，用于通知的发布日期 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 日期时间，用于日志时间与最近登录时间 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private DateTimeUtils() {
    }

    /**
     * 格式化为 yyyy-MM-dd。
     *
     * @param value 时间，允许为 null
     * @return 格式化结果；入参为 null 时返回 null
     */
    public static String formatDate(LocalDateTime value) {
        return value == null ? null : DATE.format(value);
    }

    /**
     * 格式化纯日期字段，如组织的成立时间。
     *
     * @param value 日期，允许为 null
     * @return 格式化结果；入参为 null 时返回 null
     */
    public static String formatDate(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    /**
     * 格式化为 yyyy-MM-dd HH:mm:ss。
     *
     * @param value 时间，允许为 null
     * @return 格式化结果；入参为 null 时返回 null
     */
    public static String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }
}
