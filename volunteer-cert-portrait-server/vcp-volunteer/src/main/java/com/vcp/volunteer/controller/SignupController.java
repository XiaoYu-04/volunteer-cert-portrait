package com.vcp.volunteer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.volunteer.dto.SignupAuditDTO;
import com.vcp.volunteer.dto.SignupCreateDTO;
import com.vcp.volunteer.dto.SignupQuery;
import com.vcp.volunteer.service.SignupService;
import com.vcp.volunteer.vo.SignupVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动报名接口。
 *
 * <p>列表接口被三个角色共用，因此权限写成 OR：学生持有 {@code volunteer:signup:create}，
 * 组织管理员与学校管理员持有 {@code volunteer:signup:audit}。真正的数据范围在 Service 里
 * 按登录态裁剪（学生只看自己、组织管理员只看本组织），前端传的 studentId / orgId 不作依据。
 */
@RestController
@RequestMapping("/api/v1/signups")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    /**
     * 分页查询报名记录。
     *
     * @param query 筛选条件，分页参数为 page / pageSize
     * @return 报名分页结果
     */
    @GetMapping
    @SaCheckPermission(value = {"volunteer:signup:create", "volunteer:signup:audit"}, mode = SaMode.OR)
    public R<PageResult<SignupVO>> list(SignupQuery query) {
        return R.ok(signupService.listSignups(query));
    }

    /**
     * 学生提交报名。
     *
     * @param dto 活动 id 与报名理由
     * @return 报名 id
     */
    @PostMapping
    @SaCheckPermission("volunteer:signup:create")
    @OperationLog(module = "活动报名", action = "提交报名")
    public R<Long> create(@Valid @RequestBody SignupCreateDTO dto) {
        return R.ok(signupService.createSignup(dto));
    }

    /**
     * 审核报名（通过 / 驳回）。
     *
     * @param id  报名 id
     * @param dto 审核动作与备注
     * @return 空响应
     */
    @PutMapping("/{id}/audit")
    @SaCheckPermission("volunteer:signup:audit")
    @OperationLog(module = "活动报名", action = "审核报名")
    public R<Void> audit(@PathVariable Long id, @RequestBody SignupAuditDTO dto) {
        signupService.auditSignup(id, dto);
        return R.ok();
    }

    /**
     * 学生取消报名。
     *
     * @param id 报名 id
     * @return 空响应
     */
    @PutMapping("/{id}/cancel")
    @SaCheckPermission("volunteer:signup:cancel")
    @OperationLog(module = "活动报名", action = "取消报名")
    public R<Void> cancel(@PathVariable Long id) {
        signupService.cancelSignup(id);
        return R.ok();
    }
}
