package com.vcp.org.service;

import java.math.BigDecimal;

/**
 * 组织聚合指标端口。
 *
 * <p>vcp-org 不允许直接访问 vcp-volunteer / vcp-certification 的表。
 * 后续这两个模块落地时各提供一个实现，本模块通过 Spring 注入组装；
 * 没有实现时返回空指标，保证组织基础信息不被统计模块阻塞。
 */
public interface OrgMetricsPort {

    /**
     * 取指定组织的统计指标。
     *
     * @param orgId 组织 id
     * @return 活动场次、签到率、审核通过率
     */
    OrgMetrics getMetrics(Long orgId);

    /**
     * 组织聚合指标。
     */
    record OrgMetrics(long activities, BigDecimal signRate, BigDecimal passRate) {

        public static OrgMetrics empty() {
            return new OrgMetrics(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
