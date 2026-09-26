package com.vcp.common.util;

/**
 * 字符串判空与规整工具。
 *
 * <p><b>为什么要有这个类</b>：{@code hasText} / {@code trimToNull} 这类几行小工具原先在
 * 14 个 Service 里各写了一份私有副本（横跨 6 个模块），口径漂移要改 14 处。现统一收敛到这里，
 * 各模块静态导入调用，行为与原先的私有实现逐字一致：
 *
 * <ul>
 *     <li>{@link #hasText(String)}：非 null 且去空白后非空；</li>
 *     <li>{@link #isBlank(String)}：null 或全空白；</li>
 *     <li>{@link #trimToNull(String)}：去首尾空白，空文本归 null；</li>
 *     <li>{@link #nullToEmpty(String)}：null 归空串。</li>
 * </ul>
 *
 * <p>刻意不用 Hutool 的 {@code StrUtil}：vcp-common 原先声明了 {@code hutool-all} 但全仓
 * 0 引用，本类保持零第三方依赖，用 JDK 自带的 {@code String.isBlank()} 实现。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * 是否是有内容的文本。
     *
     * @param value 待判断的字符串，允许为 null
     * @return 非 null 且去空白后长度大于 0 时为 true
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 是否是空文本。
     *
     * @param value 待判断的字符串，允许为 null
     * @return null 或全空白时为 true
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 去首尾空白后返回，空文本归 null。
     *
     * @param value 待规整的字符串，允许为 null
     * @return 去空白后的文本；空文本返回 null
     */
    public static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /**
     * null 归空串。
     *
     * @param value 待转换的字符串，允许为 null
     * @return 原值；为 null 时返回空串
     */
    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
