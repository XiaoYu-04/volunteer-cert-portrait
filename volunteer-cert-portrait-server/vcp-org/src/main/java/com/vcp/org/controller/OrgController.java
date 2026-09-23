package com.vcp.org.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.org.dto.OrgAuditDTO;
import com.vcp.org.dto.OrgQuery;
import com.vcp.org.dto.OrgStatusDTO;
import com.vcp.org.dto.OrgUpdateDTO;
import com.vcp.org.service.OrgService;
import com.vcp.org.vo.OrgVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 志愿组织接口。
 *
 * <p>列表、审核与启停是学校管理端动作；详情与资料更新保留给组织管理员操作本组织，
 * 数据范围在 Service 中校验，避免前端传入 orgId 越权读写。
 */
@RestController
@RequestMapping("/api/v1/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    @GetMapping
    @SaCheckPermission("org:info:audit")
    public R<PageResult<OrgVO>> list(OrgQuery query) {
        return R.ok(orgService.listOrgs(query));
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public R<OrgVO> detail(@PathVariable Long id) {
        return R.ok(orgService.getOrg(id));
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    @OperationLog(module = "志愿组织", action = "修改组织资料")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody OrgUpdateDTO dto) {
        orgService.updateOrg(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    @SaCheckPermission("org:info:audit")
    @OperationLog(module = "志愿组织", action = "审核资质")
    public R<Void> audit(@PathVariable Long id, @RequestBody OrgAuditDTO dto) {
        orgService.auditOrg(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("org:info:audit")
    @OperationLog(module = "志愿组织", action = "启停组织")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody OrgStatusDTO dto) {
        orgService.updateStatus(id, dto);
        return R.ok();
    }
}
