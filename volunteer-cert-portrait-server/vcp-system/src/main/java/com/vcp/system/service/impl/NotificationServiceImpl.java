package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.enums.NotificationTypeEnum;
import com.vcp.common.enums.UserStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.system.dto.NotificationCreateDTO;
import com.vcp.system.dto.NotificationQuery;
import com.vcp.system.entity.Notification;
import com.vcp.system.entity.SysUser;
import com.vcp.system.mapper.NotificationMapper;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.service.NotificationService;
import com.vcp.system.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.vcp.common.util.StringUtils.hasText;

/**
 * 通知公告服务实现。
 *
 * <p><b>为什么按收件人一行存</b>：is_read 是"这一行被这个人读了"，天然按人记。
 * 代价是群发 N 行，换来的是已读状态不需要额外的关联表，查询也不用 JOIN ——
 * 对毕设这个量级完全划算。真要支撑万级用户时，正确做法是改成
 * "公告表 + 已读记录表"，届时只需改本类，接口契约不动。
 *
 * <p><b>越权一律报 10004 而不是 20003</b>：报"无权限"等于确认这条通知存在。
 * 通知是私人数据，不该让任何人通过错误码探测出别人收到过什么。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 未读筛选的生效取值，与前端页签传参一致 */
    private static final String UNREAD_FLAG = "true";

    /** 发布方缺省值，与前端表单的默认值一致 */
    private static final String DEFAULT_SOURCE = "系统管理员";

    private final NotificationMapper notificationMapper;

    private final SysUserMapper userMapper;

    /**
     * 分页查询当前用户的通知。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<NotificationVO> listNotifications(NotificationQuery query) {
        NotificationQuery condition = query == null ? new NotificationQuery() : query;
        Long userId = AuthUtils.getUserId();

        LambdaQueryWrapper<Notification> wrapper = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId);

        if (hasText(condition.getKeyword())) {
            wrapper.like(Notification::getTitle, condition.getKeyword().trim());
        }
        if (hasText(condition.getType())) {
            wrapper.eq(Notification::getType, condition.getType().trim());
        }
        // 只认 "true"：前端这个参数有三种形态（空串 / 'true' / 布尔），
        // 布尔经 axios 序列化后也是 "true" 文本，统一按字符串收下最稳。
        if (UNREAD_FLAG.equalsIgnoreCase(condition.getUnreadOnly())) {
            wrapper.eq(Notification::getIsRead, false);
        }

        // 置顶在前、同组内新的在前：置顶的意义就是"不被新消息顶下去"。
        // is_top 为 NULL 的行在 PostgreSQL 的 DESC 排序里排在最前，
        // 而库里 is_top 有默认值 false，正常不会出现 NULL。
        wrapper.orderByDesc(Notification::getTop).orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);

        Page<Notification> page = notificationMapper.selectPage(PageUtils.toPage(condition), wrapper);
        // 列表不需要正文，不查 content 列可以少传一截文本；本方法复用了详情用的
        // toVO，正文一并带出，量小可接受，将来正文变长时再拆一个列表专用的转换。
        return PageUtils.page(page, this::toVO);
    }

    /**
     * 取通知详情并标记为已读。
     *
     * @param id 通知 id
     * @return 通知详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO getNotification(Long id) {
        Notification notification = requireOwnNotification(id);

        // 已经是已读就不写库：详情页反复打开是常态，每次 UPDATE 都会刷新
        // 行版本并产生无意义的 WAL 写入。
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            Notification patch = new Notification();
            patch.setId(notification.getId());
            patch.setIsRead(true);
            notificationMapper.updateById(patch);
            notification.setIsRead(true);
        }
        return toVO(notification);
    }

    /**
     * 标记通知为已读。
     *
     * @param id 通知 id
     */
    @Override
    public void markRead(Long id) {
        Notification notification = requireOwnNotification(id);
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }
        Notification patch = new Notification();
        patch.setId(notification.getId());
        patch.setIsRead(true);
        notificationMapper.updateById(patch);
    }

    /**
     * 发布公告（群发）。
     *
     * @param dto 公告内容
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(NotificationCreateDTO dto) {
        if (dto == null || !hasText(dto.getTitle())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请输入公告标题");
        }

        String type = resolveType(dto.getType());
        String source = hasText(dto.getFrom()) ? dto.getFrom().trim() : DEFAULT_SOURCE;
        boolean top = Boolean.TRUE.equals(dto.getTop());
        // 正文可留空：与 createForUser 同一处理，空串统一存 null 而不是 ''
        String content = hasText(dto.getContent()) ? dto.getContent().trim() : null;
        String batchNo = UUID.randomUUID().toString().replace("-", "");

        List<Long> recipients = enabledUserIds();
        if (recipients.isEmpty()) {
            // 一个启用账号都没有时不报错：公告本身没问题，只是没人收。
            // 报错会让管理员以为是自己填错了内容。
            log.warn("[通知] 发布公告时没有启用中的账号，未产生任何通知。title={}", dto.getTitle());
            return;
        }

        for (Long userId : recipients) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(dto.getTitle().trim());
            notification.setContent(content);
            notification.setType(type);
            notification.setSource(source);
            notification.setTop(top);
            notification.setIsRead(false);
            notification.setBatchNo(batchNo);
            notificationMapper.insert(notification);
        }
        log.info("[通知] 公告已发布。batchNo={}, 收件人={} 人, title={}",
                batchNo, recipients.size(), dto.getTitle());
    }

    /**
     * 给单个用户落一行通知。
     *
     * <p>这里不复用群发方法：群发会按“所有启用账号”取收件人，
     * 而资质审核只该通知该组织的负责人。
     *
     * @param userId  收件人 id
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型
     * @param source  通知来源
     */
    @Override
    public void createForUser(Long userId, String title, String content, String type, String source) {
        if (userId == null || !hasText(title)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "通知收件人与标题不能为空");
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title.trim());
        notification.setContent(hasText(content) ? content.trim() : null);
        notification.setType(resolveType(type));
        notification.setSource(hasText(source) ? source.trim() : DEFAULT_SOURCE);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
    }

    /**
     * 删除公告：按批次号整批删除。
     *
     * @param id 通知 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Notification notification = requireOwnNotification(id);
        if (hasText(notification.getBatchNo())) {
            // 群发公告：只删自己那一行的话，学生端仍然看得到，
            // 表现为"删了但没删掉"，是这条业务最容易踩的坑。
            int removed = notificationMapper.delete(Wrappers.<Notification>lambdaQuery()
                    .eq(Notification::getBatchNo, notification.getBatchNo()));
            log.info("[通知] 公告已整批删除。batchNo={}, 共 {} 行",
                    notification.getBatchNo(), removed);
            return;
        }
        notificationMapper.deleteById(notification.getId());
    }

    /**
     * 取通知，并确认它属于当前登录人。
     *
     * <p>归属校验放在这里而不是散在各个方法里：三个方法都要做同一件事，
     * 漏掉任何一处就是一条越权读取别人通知的路径。
     *
     * @param id 通知 id
     * @return 通知实体
     * @throws BusinessException 通知不存在、已删除，或不属于当前用户（10004）
     */
    private Notification requireOwnNotification(Long id) {
        Notification notification = id == null ? null : notificationMapper.selectById(id);
        // 不区分"不存在"与"不是你的"：两种情况的对外表现必须一致，
        // 否则错误码本身就泄露了"这条通知存在"。
        if (notification == null || !AuthUtils.getUserId().equals(notification.getUserId())) {
            throw new BusinessException(ErrorCodeEnum.NOTIFICATION_NOT_FOUND);
        }
        return notification;
    }

    /**
     * 归一化通知类型。
     *
     * <p>认不出的类型退回 SYSTEM 而不是报错：公告内容本身是好的，
     * 因为一个类型码把整条发布挡下来得不偿失；而退回 SYSTEM 至少能让它出现在列表里。
     *
     * @param type 前端传入的类型，允许为空
     * @return 合法的类型码
     */
    private String resolveType(String type) {
        NotificationTypeEnum matched = NotificationTypeEnum.of(type == null ? null : type.trim());
        return matched == null ? NotificationTypeEnum.SYSTEM.getCode() : matched.getCode();
    }

    /**
     * 取全部启用中的账号 id，作为群发的收件人。
     *
     * <p>只取 id 不取整行：群发只需要知道发给谁，把实体全查出来会白白搬运
     * 一批含明文密码的对象。逻辑删除由 {@code @TableLogic} 自动过滤。
     *
     * @return 用户 id 列表
     */
    private List<Long> enabledUserIds() {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId)
                .eq(SysUser::getStatus, UserStatusEnum.ACTIVE.getDbValue()));
        return users.stream().map(SysUser::getId).toList();
    }

    /**
     * 通知实体转响应对象。
     *
     * <p>两处改名是刻意的：{@code source → from}（from 是 SQL 关键字，列名用不了）、
     * {@code isRead → read}。日期格式化成字符串，页面是直接渲染的。
     *
     * @param notification 通知实体
     * @return 响应对象
     */
    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setTitle(notification.getTitle());
        vo.setDate(DateTimeUtils.formatDate(notification.getCreateTime()));
        vo.setFrom(notification.getSource());
        vo.setTop(Boolean.TRUE.equals(notification.getTop()));
        vo.setType(notification.getType());
        vo.setRead(Boolean.TRUE.equals(notification.getIsRead()));
        vo.setContent(notification.getContent());
        return vo;
    }
}
