package com.vcp.system.service;

import com.vcp.common.result.PageResult;
import com.vcp.system.dto.NotificationCreateDTO;
import com.vcp.system.dto.NotificationQuery;
import com.vcp.system.vo.NotificationVO;

/**
 * 通知公告服务。
 *
 * <p><b>数据范围一律由登录态决定，请求体里没有 userId</b>。通知按"每个收件人一行"
 * 存储（is_read 天然是按人记的），因此列表、详情、标记已读三个方法都只认当前登录人，
 * 越权访问别人的通知会得到 10004「通知不存在」而不是 20003 —— 报"无权限"等于
 * 告诉对方"这条通知确实存在"，属于信息泄露。
 *
 * <p>发布与删除是管理端动作，接口层用 {@code system:notice:manage} 把住。
 */
public interface NotificationService {

    /**
     * 分页查询当前用户的通知。
     *
     * @param query 查询条件，unreadOnly 取 "true" 时只看未读
     * @return 分页结果，置顶的排在最前
     */
    PageResult<NotificationVO> listNotifications(NotificationQuery query);

    /**
     * 取通知详情，<b>并顺带标记为已读</b>。
     *
     * <p>标记已读放在详情接口里而不是让前端多调一次：前端详情页打开就是"看过了"，
     * 分两步调用一旦第二步失败，就会出现"点开过但仍是未读"的状态。
     *
     * @param id 通知 id
     * @return 通知详情
     * @throws com.vcp.common.exception.BusinessException 通知不存在或不属于当前用户（10004）
     */
    NotificationVO getNotification(Long id);

    /**
     * 标记通知为已读。
     *
     * @param id 通知 id
     * @throws com.vcp.common.exception.BusinessException 通知不存在或不属于当前用户（10004）
     */
    void markRead(Long id);

    /**
     * 发布公告（群发）。
     *
     * <p>为每个启用中的账号插入一行，并用同一个批次号串起来。批次号是删除的依据：
     * 管理员手里只有自己那一行的 id，按 id 删只会删掉自己的一份，学生端照样看得到。
     *
     * <p>{@code dto.content}（正文）可留空，留空即只发标题；学生端详情页会渲染该字段。
     *
     * @param dto 公告内容
     * @throws com.vcp.common.exception.BusinessException 标题为空（10001）
     */
    void create(NotificationCreateDTO dto);

    /**
     * 给单个用户发送系统通知。
     *
     * <p>组织资质审核等业务只需要通知一个负责人，不适合复用“群发公告”的接口；
     * 该方法只负责落一行通知，不进入 Controller。
     *
     * @param userId  收件人 id
     * @param title   通知标题
     * @param content 通知正文，允许为空
     * @param type    通知类型，空值按 SYSTEM 处理
     * @param source  通知来源，空值按「系统管理员」处理
     * @throws com.vcp.common.exception.BusinessException userId 为空或标题为空（10001）
     */
    void createForUser(Long userId, String title, String content, String type, String source);

    /**
     * 删除公告：按批次号整批删除，没有批次号时只删这一行。
     *
     * @param id 通知 id
     * @throws com.vcp.common.exception.BusinessException 通知不存在（10004）
     */
    void delete(Long id);
}
