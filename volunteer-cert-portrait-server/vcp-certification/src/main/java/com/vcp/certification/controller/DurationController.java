package com.vcp.certification.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.certification.dto.DurationAuditDTO;
import com.vcp.certification.dto.DurationBatchAuditDTO;
import com.vcp.certification.dto.DurationQuery;
import com.vcp.certification.dto.DurationSubmitDTO;
import com.vcp.certification.service.DurationAuditService;
import com.vcp.certification.service.DurationService;
import com.vcp.certification.vo.DurationBatchAuditVO;
import com.vcp.certification.vo.DurationSubmitVO;
import com.vcp.certification.vo.DurationVO;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务时长接口。
 *
 * <p><b>列表与详情只校验登录、不挂权限标识</b>：学生端「我的时长」、组织端「时长提交」、
 * 学校端「时长审核」三个页面共用同一个列表接口，挂上任一权限标识都会把另外两个角色
 * 打成 20003。数据范围改由登录态在 Service 里限定（学生只看自己、组织只看本组织），
 * 这也正是前端 mock 里被漏掉、必须由后端守住的那一环。
 *
 * <p>提交与审核是各自角色的专属动作，分别挂 {@code certification:duration:submit}（组织管理员）
 * 与 {@code certification:duration:approve}（学校管理员）。
 *
 * <p>路由注意：{@code /batch-audit} 是 POST，{@code /{id}} 是 GET，两者不会互相抢先匹配
 * （前端契约里 batch-audit 也只有 POST）。
 */
@RestController
@RequestMapping("/api/v1/durations")
@RequiredArgsConstructor
public class DurationController {

    private final DurationService durationService;

    private final DurationAuditService durationAuditService;

    /**
     * 分页查询服务时长记录。
     *
     * @param query 查询条件，分页参数为 page / pageSize
     * @return 分页结果
     */
    @GetMapping
    @SaCheckLogin
    public R<PageResult<DurationVO>> list(DurationQuery query) {
        return R.ok(durationService.listDurations(query));
    }

    /**
     * 查询单条服务时长记录。
     *
     * @param id 时长记录 id
     * @return 记录详情
     */
    @GetMapping("/{id}")
    @SaCheckLogin
    public R<DurationVO> detail(@PathVariable Long id) {
        return R.ok(durationService.getDuration(id));
    }

    /**
     * 提交服务时长（支持单条与批量两种请求体形状）。
     *
     * @param dto 提交内容
     * @return 本次提交的记录 id 列表
     */
    @PostMapping
    @SaCheckPermission("certification:duration:submit")
    @OperationLog(module = "时长认证", action = "提交服务时长")
    public R<DurationSubmitVO> submit(@RequestBody DurationSubmitDTO dto) {
        return R.ok(DurationSubmitVO.of(durationService.submitDurations(dto)));
    }

    /**
     * 审核单条服务时长。
     *
     * @param id  时长记录 id
     * @param dto 审核动作与备注
     * @return 空响应
     */
    @PutMapping("/{id}/audit")
    @SaCheckPermission("certification:duration:approve")
    @OperationLog(module = "时长认证", action = "审核服务时长")
    public R<Void> audit(@PathVariable Long id, @RequestBody DurationAuditDTO dto) {
        durationAuditService.audit(id, dto);
        return R.ok();
    }

    /**
     * 批量审核服务时长。
     *
     * @param dto 记录 id 列表、审核动作与备注
     * @return 实际处理条数
     */
    @PostMapping("/batch-audit")
    @SaCheckPermission("certification:duration:approve")
    @OperationLog(module = "时长认证", action = "批量审核服务时长")
    public R<DurationBatchAuditVO> batchAudit(@RequestBody DurationBatchAuditDTO dto) {
        return R.ok(DurationBatchAuditVO.of(durationAuditService.batchAudit(dto)));
    }
}
