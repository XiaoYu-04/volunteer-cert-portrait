package com.vcp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数值转换与比率计算工具。
 *
 * <p><b>为什么要有这个类</b>：聚合结果转 long（{@code toLong}）、计数兜 0
 * （{@code valueOf}）与求比率（{@code ratio}）这三类小工具，原先在 vcp-system / vcp-org /
 * vcp-volunteer 的 Service 里各写了一份私有副本（跨模块 6 处），口径漂移要改多处。
 * 现统一收敛到这里，各调用点静态导入调用，行为与原先的私有实现逐字一致：
 *
 * <ul>
 *     <li>{@link #toLong(Object)}：Number 直转，非 Number 兜底字符串解析，null 归 0；</li>
 *     <li>{@link #longValue(Long)}：null 归 0；</li>
 *     <li>{@link #ratio(Long, Long)}：0 ~ 1 的比率，默认保留 4 位小数；</li>
 *     <li>{@link #ratio(Long, Long, int)}：同上，小数位由调用方指定。</li>
 * </ul>
 *
 * <p><b>比率的口径</b>：分母为 null、0 或负数时返回 0（按目标小数位补零），否则按 HALF_UP
 * 舍入；返回的是 0 ~ 1 的小数，前端用 formatPercent 渲染成百分数（只取 1 位百分数，
 * 因此默认 4 位小数足够）。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class NumberUtils {

    /** 比率默认小数位：前端 formatPercent 只取 1 位百分数，4 位小数足够且不丢精度 */
    private static final int DEFAULT_RATIO_SCALE = 4;

    private NumberUtils() {
    }

    /**
     * 聚合结果转 long。
     *
     * <p>COUNT(*) 在 PostgreSQL 里是 int8，但 selectMaps 取出来的值类型随驱动与列类型变化
     * （可能是 Integer / Long / BigDecimal），统一按 Number 转，兜底再走一次字符串解析。
     *
     * @param value 聚合结果值
     * @return 数值，null 时返回 0
     */
    public static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    /**
     * 计数或聚合值去 null。
     *
     * @param value 计数或聚合值，允许为 null
     * @return 原值；为 null 时返回 0
     */
    public static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 求比率（0 ~ 1 的小数），默认保留 4 位小数，前端用 formatPercent 渲染成百分数。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率；分母为 0 或缺失时返回 0
     */
    public static BigDecimal ratio(Long numerator, Long denominator) {
        return ratio(numerator, denominator, DEFAULT_RATIO_SCALE);
    }

    /**
     * 求比率（0 ~ 1 的小数），小数位由调用方指定。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @param scale       小数位
     * @return 比率；分母为 0 或缺失时返回 0（按 scale 保留小数位）
     */
    public static BigDecimal ratio(Long numerator, Long denominator, int scale) {
        if (numerator == null || denominator == null || denominator <= 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }
}
