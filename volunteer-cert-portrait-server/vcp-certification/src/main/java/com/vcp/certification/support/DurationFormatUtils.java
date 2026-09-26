package com.vcp.certification.support;

import java.math.BigDecimal;

/**
 * 时长文本格式化：把时长写成日志与通知正文里的可读文本。
 *
 * <p>{@link #plainHours(BigDecimal)} 原先在 {@code DurationServiceImpl} 与
 * {@code DurationAuditServiceImpl} 里各有一份逐字相同的私有副本，这里收敛成一处，
 * 保证「提交流水备注」与「审核结果通知」两个出口对同一笔时长的写法永远一致。
 *
 * <p>放在 {@code support} 包而不是 vcp-common：「0 显示为 0」「不留科学计数法」是本模块
 * 对外文案的口径，别的域不需要替这个约定买单。类与方法是 public 仅因为两个调用点在
 * {@code service.impl} 包、跨包访问所需，实际只在本模块内使用。
 */
public final class DurationFormatUtils {

    private DurationFormatUtils() {
    }

    /**
     * 把时长转成不带多余小数位、也不出现科学计数法的文本。
     *
     * <p>{@code stripTrailingZeros()} 去掉 {@code 1.0 → 1} 的小数尾巴；
     * {@code toPlainString()} 再把 {@code stripTrailingZeros()} 可能产生的
     * {@code 6E+2} 摊平成 {@code 600}。null 按 {@code "0"} 处理：时长列可空，
     * 两个调用点都不做空判断，原实现即如此。
     *
     * @param hours 时长（小时），允许为 null
     * @return 形如 {@code 2.5}、{@code 3}、{@code 0} 的文本
     */
    public static String plainHours(BigDecimal hours) {
        return hours == null ? "0" : hours.stripTrailingZeros().toPlainString();
    }
}
