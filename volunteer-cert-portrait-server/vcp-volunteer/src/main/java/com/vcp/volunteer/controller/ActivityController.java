package com.vcp.volunteer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.volunteer.dto.ActivityQuery;
import com.vcp.volunteer.dto.ActivitySaveDTO;
import com.vcp.volunteer.dto.ActivityStatusDTO;
import com.vcp.volunteer.service.ActivityService;
import com.vcp.volunteer.vo.ActivityVO;
import com.vcp.volunteer.vo.OrgOverviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 志愿活动接口。
 *
 * <p>列表与详情对学生、组织管理员、学校管理员开放（三个角色都持有
 * {@code volunteer:activity:list}），可见范围由 Service 按登录态裁剪：
 * 学生看不到草稿，组织管理员只看本组织，学校管理员看全量。
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 组织端数据概览。
     *
     * <p><b>必须声明在 {@code /{id}} 之前</b>：否则 org-overview 会被当成活动 id
     * （前端 mock 路由里踩过同一个坑，见待办 B19）。
     *
     * @param orgId 组织 id；组织管理员不传也会被登录态覆盖
     * @return 组织资料与指标卡
     */
    @GetMapping("/org-overview")
    @SaCheckPermission("volunteer:activity:list")
    public R<OrgOverviewVO> orgOverview(@RequestParam(required = false) Long orgId) {
        return R.ok(activityService.getOrgOverview(orgId));
    }

    /**
     * 分页查询活动。
     *
     * @param query 筛选条件，分页参数为 page / pageSize
     * @return 活动分页结果
     */
    @GetMapping
    @SaCheckPermission("volunteer:activity:list")
    public R<PageResult<ActivityVO>> list(ActivityQuery query) {
        return R.ok(activityService.listActivities(query));
    }

    /**
     * 活动详情。
     *
     * @param id 活动 id
     * @return 活动详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("volunteer:activity:list")
    public R<ActivityVO> detail(@PathVariable Long id) {
        return R.ok(activityService.getActivity(id));
    }

    /**
     * 新建活动（落库为草稿）。
     *
     * @param dto 活动内容
     * @return 新活动 id
     */
    @PostMapping
    @SaCheckPermission("volunteer:activity:create")
    @OperationLog(module = "志愿活动", action = "新建活动")
    public R<Long> create(@Valid @RequestBody ActivitySaveDTO dto) {
        return R.ok(activityService.createActivity(dto));
    }

    /**
     * 修改活动。
     *
     * @param id  活动 id
     * @param dto 待更新字段
     * @return 空响应
     */
    @PutMapping("/{id}")
    @SaCheckPermission("volunteer:activity:update")
    @OperationLog(module = "志愿活动", action = "修改活动")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ActivitySaveDTO dto) {
        activityService.updateActivity(id, dto);
        return R.ok();
    }

    /**
     * 活动状态流转（发布 / 结束 / 取消）。
     *
     * @param id  活动 id
     * @param dto 目标状态
     * @return 空响应
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("volunteer:activity:publish")
    @OperationLog(module = "志愿活动", action = "更新活动状态")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody ActivityStatusDTO dto) {
        activityService.updateStatus(id, dto);
        return R.ok();
    }
}
