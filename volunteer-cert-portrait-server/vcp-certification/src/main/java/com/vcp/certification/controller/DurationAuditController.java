package com.vcp.certification.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.certification.service.DurationAuditService;
import com.vcp.certification.vo.DurationAuditSummaryVO;
import com.vcp.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时长审核统计接口。
 *
 * <p>单独成一个 Controller 是因为前端把统计挂在另一个资源路径下
 * （{@code /v1/duration-audits/summary}），与时长记录的 {@code /v1/durations} 分开；
 * 调用方只有学校管理端的审核页，因此直接挂 {@code certification:duration:approve}。
 */
@RestController
@RequestMapping("/api/v1/duration-audits")
@RequiredArgsConstructor
public class DurationAuditController {

    private final DurationAuditService durationAuditService;

    /**
     * 取审核三态统计（全校口径）。
     *
     * @return 审核总量、通过率与三态分布
     */
    @GetMapping("/summary")
    @SaCheckPermission("certification:duration:approve")
    public R<DurationAuditSummaryVO> summary() {
        return R.ok(durationAuditService.getSummary());
    }
}
