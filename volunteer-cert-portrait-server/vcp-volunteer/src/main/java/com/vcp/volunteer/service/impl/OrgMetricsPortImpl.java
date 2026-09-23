package com.vcp.volunteer.service.impl;

import com.vcp.org.service.OrgMetricsPort;
import com.vcp.volunteer.mapper.VolunteerActivityMapper;
import com.vcp.volunteer.mapper.row.OrgOverviewRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 组织聚合指标端口实现：把本域的活动 / 报名 / 签到计数回填给 vcp-org。
 *
 * <p><b>为什么要有这个类</b>：{@code OrgVO} 上的 activities / signRate / passRate 三个字段
 * 只有本域算得出来，而 vcp-org 不能反向依赖 vcp-volunteer（依赖方向见 CLAUDE.md），
 * 于是它在 vcp-org 侧定义端口、用 {@code ObjectProvider} 取实现，由本模块提供实现。
 * 没有这个 Bean 时组织列表与详情页的「累计活动 / 签到率 / 通过率」会全部显示成 0。
 *
 * <p><b>刻意不调 OrgService</b>：本类被 {@code OrgServiceImpl} 的 toVO 调用，
 * 再回头调 OrgService 会形成递归。这里只查自己域的聚合 SQL。
 *
 * <p>比率一律返回 0 ~ 1 的小数：前端用 formatPercent 渲染成百分数
 * （OrgList 与 OrgAuditView 都按这个口径取值）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgMetricsPortImpl implements OrgMetricsPort {

    /** 比率小数位，与前端百分数展示（保留 1 位）匹配 */
    private static final int RATIO_SCALE = 4;

    private final VolunteerActivityMapper activityMapper;

    @Override
    public OrgMetrics getMetrics(Long orgId) {
        if (orgId == null) {
            return OrgMetrics.empty();
        }
        OrgOverviewRow row = activityMapper.selectOrgOverview(orgId);
        if (row == null) {
            return OrgMetrics.empty();
        }
        long approved = valueOf(row.getApprovedSignupTotal());
        long rejected = valueOf(row.getRejectedSignupTotal());
        return new OrgMetrics(
                valueOf(row.getActivityTotal()),
                ratio(row.getSignedAttendanceTotal(), row.getAttendanceTotal()),
                ratio(approved, approved + rejected));
    }

    private static long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 求比率（0 ~ 1）。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率；分母为 0 或缺失时返回 0
     */
    private static BigDecimal ratio(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
