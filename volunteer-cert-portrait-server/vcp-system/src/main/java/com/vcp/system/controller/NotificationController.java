package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.dto.NotificationCreateDTO;
import com.vcp.system.dto.NotificationQuery;
import com.vcp.system.service.NotificationService;
import com.vcp.system.vo.NotificationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知公告接口。
 *
 * <p><b>读接口不加权限校验</b>：学生端的「通知公告」页、管理端的「公告管理」页
 * 共用同一个列表接口，数据范围由登录态决定（每个收件人一行），
 * 因此不需要也不能用 {@code system:notice:manage} 把它挡住 ——
 * 挂上权限会直接把学生的通知页打成 20003。
 *
 * <p>发布与删除是管理端动作，挂 {@code system:notice:manage}（仅学校管理员持有）。
 */
@RestController
@RequestMapping("/api/v1/system/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前用户的通知。
     *
     * @param query 查询条件，unreadOnly 取 "true" 时只看未读
     * @return 分页结果
     */
    @GetMapping
    public R<PageResult<NotificationVO>> list(NotificationQuery query) {
        return R.ok(notificationService.listNotifications(query));
    }

    /**
     * 取通知详情，顺带标记为已读。
     *
     * @param id 通知 id
     * @return 通知详情
     */
    @GetMapping("/{id}")
    public R<NotificationVO> detail(@PathVariable Long id) {
        return R.ok(notificationService.getNotification(id));
    }

    /**
     * 标记通知为已读。
     *
     * @param id 通知 id
     * @return 空响应
     */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    /**
     * 发布公告（群发）。
     *
     * @param dto 公告内容
     * @return 空响应
     */
    @PostMapping
    @SaCheckPermission("system:notice:manage")
    @OperationLog(module = "用户与权限", action = "发布公告")
    public R<Void> create(@Valid @RequestBody NotificationCreateDTO dto) {
        notificationService.create(dto);
        return R.ok();
    }

    /**
     * 删除公告（按批次整批删除）。
     *
     * @param id 通知 id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:notice:manage")
    @OperationLog(module = "用户与权限", action = "删除公告")
    public R<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return R.ok();
    }
}
