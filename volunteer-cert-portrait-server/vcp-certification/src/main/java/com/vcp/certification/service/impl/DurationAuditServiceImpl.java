package com.vcp.certification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.certification.dto.DurationAuditDTO;
import com.vcp.certification.dto.DurationBatchAuditDTO;
import com.vcp.certification.entity.DurationAudit;
import com.vcp.certification.entity.ServiceDuration;
import com.vcp.certification.mapper.ActivityBrief;
import com.vcp.certification.mapper.DurationAuditMapper;
import com.vcp.certification.mapper.DurationRefMapper;
import com.vcp.certification.mapper.ServiceDurationMapper;
import com.vcp.certification.service.DurationAuditService;
import com.vcp.certification.vo.DurationAuditSummaryVO;
import com.vcp.certification.vo.DurationSummaryItemVO;
import com.vcp.common.enums.AuditActionEnum;
import com.vcp.common.enums.DurationStatusEnum;
import com.vcp.common.enums.NotificationTypeEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.framework.security.AuthUtils;
import com.vcp.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 服务时长审核服务实现（学校管理员）。
 *
 * <p><b>审核通过要做三件事，缺一不可</b>：
 * <ol>
 *   <li>把记录置为 APPROVED 并记下审核人 / 审核时间；</li>
 *   <li>累加 {@code student_info.total_duration}（累计时长的唯一事实来源）；</li>
 *   <li>给学生落一条 DURATION 类型通知。</li>
 * </ol>
 * 三件事在同一事务里完成：只改状态不累加时长，学生永远拿不到时长；累加了不通知，
 * 学生端看不到结果。驳回则跳过第 2 步，并把驳回理由写进审核备注与通知正文。
 *
 * <p><b>动作与状态不同形</b>：流水里记的是动作 {@code APPROVE / REJECT}，
 * 状态置的是 {@code APPROVED / REJECTED}，两者不可混用（见 AuditActionEnum）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurationAuditServiceImpl implements DurationAuditService {

    /** 审核备注上限，与 service_duration.audit_remark 的 VARCHAR(255) 对齐 */
    private static final int MAX_REMARK_LENGTH = 255;

    /** 通知来源，展示在通知列表的「来源」列 */
    private static final String NOTIFICATION_SOURCE = "学校管理员";

    private final ServiceDurationMapper serviceDurationMapper;

    private final DurationAuditMapper durationAuditMapper;

    private final DurationRefMapper refMapper;

    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, DurationAuditDTO dto) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "缺少时长记录ID");
        }
        AuditActionEnum action = resolveAction(dto == null ? null : dto.getAction());
        String remark = resolveRemark(action, dto == null ? null : dto.getRemark());

        ServiceDuration duration = serviceDurationMapper.selectById(id);
        if (duration == null) {
            throw new BusinessException(ErrorCodeEnum.DURATION_NOT_FOUND);
        }
        if (!DurationStatusEnum.PENDING_AUDIT.getCode().equals(duration.getStatus())) {
            // 重复通过会把同一个学生的累计时长算两遍，所以必须在累加之前挡住。
            // 写法与 vcp-org 的资质审核一致（先读后判再更新），能挡住重复点击与重放；
            // 库里没有 version 列（见待办 B17），两人同一瞬间点「通过」的极端并发不在覆盖范围内。
            throw new BusinessException(ErrorCodeEnum.DURATION_ALREADY_AUDITED);
        }
        settle(duration, action, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAudit(DurationBatchAuditDTO dto) {
        AuditActionEnum action = resolveAction(dto == null ? null : dto.getAction());
        // 备注与动作在循环外校验一次：一条不合格的请求不该先把前几条处理掉再报错
        String remark = resolveRemark(action, dto == null ? null : dto.getRemark());

        List<Long> ids = dto == null || dto.getIds() == null
                ? List.of()
                : dto.getIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请先勾选要处理的时长记录");
        }

        int done = 0;
        for (Long id : ids) {
            ServiceDuration duration = serviceDurationMapper.selectById(id);
            // 已审核或已被删除的记录直接跳过：批量操作面向的是「当前页待审核」，
            // 列表可能已经过期，为一条过期数据让整批失败不合理（与前端 mock 行为一致）
            if (duration == null || !DurationStatusEnum.PENDING_AUDIT.getCode().equals(duration.getStatus())) {
                continue;
            }
            settle(duration, action, remark);
            done++;
        }
        log.info("[时长认证] 批量审核完成。action={}, 实际处理 {} 条 / 请求 {} 条",
                action.getCode(), done, ids.size());
        return done;
    }

    @Override
    public DurationAuditSummaryVO getSummary() {
        long approved = countByStatus(DurationStatusEnum.APPROVED);
        long pending = countByStatus(DurationStatusEnum.PENDING_AUDIT);
        long rejected = countByStatus(DurationStatusEnum.REJECTED);
        // total 只统计三态：PENDING_SUBMIT 那批还没提交，不参与审核口径，
        // 放进分母会让通过率凭空变低
        long total = approved + pending + rejected;

        DurationAuditSummaryVO vo = new DurationAuditSummaryVO();
        vo.setTotal(total);
        vo.setPassRate(total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(approved).divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP));
        // 顺序是契约：前端按下标取 items[0]（已通过）与 items[2]（已驳回）
        vo.setItems(List.of(
                DurationSummaryItemVO.of("已通过", approved, "ok"),
                DurationSummaryItemVO.of("待审核", pending, "warn"),
                DurationSummaryItemVO.of("已驳回", rejected, "bad")));
        vo.setPendingInQueue(pending);
        return vo;
    }

    /**
     * 落定一条审核结论：改状态 → 写流水 → 通过则累加时长 → 通知学生。
     *
     * @param duration 时长记录（此时必为 PENDING_AUDIT）
     * @param action   审核动作
     * @param remark   审核备注；驳回时为驳回理由
     */
    private void settle(ServiceDuration duration, AuditActionEnum action, String remark) {
        boolean approved = action == AuditActionEnum.APPROVE;
        Long auditorId = AuthUtils.getUserId();

        ServiceDuration patch = new ServiceDuration();
        patch.setId(duration.getId());
        patch.setStatus(approved ? DurationStatusEnum.APPROVED.getCode() : DurationStatusEnum.REJECTED.getCode());
        patch.setAuditUserId(auditorId);
        patch.setAuditTime(LocalDateTime.now());
        // 通过时把备注清空：重新提交过的记录可能还留着上一轮的驳回理由
        patch.setAuditRemark(approved ? null : remark);
        serviceDurationMapper.updateById(patch);

        recordFlow(duration.getId(), auditorId, action, remark);

        if (approved) {
            accumulate(duration);
        }
        notifyStudent(duration, action, remark);
    }

    /**
     * 把通过的时长累加到学生累计有效时长上。
     *
     * @param duration 时长记录
     */
    private void accumulate(ServiceDuration duration) {
        BigDecimal hours = duration.getDuration();
        if (duration.getStudentId() == null || hours == null || hours.signum() <= 0) {
            log.warn("[时长认证] 记录缺少学生或时长，未累加累计时长。durationId={}", duration.getId());
            return;
        }
        int updated = refMapper.addStudentTotalDuration(duration.getStudentId(), hours);
        if (updated == 0) {
            // 档案被逻辑删除等极端情况：审核结论本身有效，不回滚整笔，但必须留下痕迹
            log.warn("[时长认证] 学生档案不存在或已删除，累计时长未累加。durationId={}, studentId={}",
                    duration.getId(), duration.getStudentId());
        }
    }

    /**
     * 给提交这条时长的学生发审核结果通知（按收件人一行存）。
     *
     * @param duration 时长记录
     * @param action   审核动作
     * @param remark   驳回理由
     */
    private void notifyStudent(ServiceDuration duration, AuditActionEnum action, String remark) {
        Long userId = duration.getStudentId() == null
                ? null
                : refMapper.selectStudentUserId(duration.getStudentId());
        if (userId == null) {
            // 通知是审核的附带动作，档案缺失不该让整笔审核回滚，但日志里要留痕
            log.warn("[时长认证] 学生档案或关联账号缺失，未发送审核通知。durationId={}, studentId={}",
                    duration.getId(), duration.getStudentId());
            return;
        }

        ActivityBrief activity = duration.getActivityId() == null
                ? null
                : refMapper.selectActivityBrief(duration.getActivityId());
        String activityTitle = activity == null || !hasText(activity.getTitle())
                ? "志愿活动"
                : activity.getTitle();
        String hours = plainHours(duration.getDuration());

        if (action == AuditActionEnum.APPROVE) {
            notificationService.createForUser(userId, "服务时长审核通过",
                    "您参与的「" + activityTitle + "」服务时长 " + hours + " 小时已通过审核，已计入累计志愿时长。",
                    NotificationTypeEnum.DURATION.getCode(), NOTIFICATION_SOURCE);
        } else {
            notificationService.createForUser(userId, "服务时长审核驳回",
                    "您参与的「" + activityTitle + "」服务时长 " + hours + " 小时被驳回：" + remark,
                    NotificationTypeEnum.DURATION.getCode(), NOTIFICATION_SOURCE);
        }
    }

    /**
     * 写一条审核流水。
     *
     * @param durationId 时长记录 id
     * @param auditorId  审核人 id
     * @param action     动作
     * @param remark     备注
     */
    private void recordFlow(Long durationId, Long auditorId, AuditActionEnum action, String remark) {
        DurationAudit flow = new DurationAudit();
        flow.setDurationId(durationId);
        flow.setAuditorId(auditorId);
        flow.setAction(action.getCode());
        flow.setRemark(remark);
        durationAuditMapper.insert(flow);
    }

    /**
     * 校验审核动作。
     *
     * @param action 请求里的动作码
     * @return 动作枚举
     * @throws BusinessException 动作缺失、非法或传了组织端的 SUBMIT（10001）
     */
    private AuditActionEnum resolveAction(String action) {
        AuditActionEnum resolved = AuditActionEnum.of(trimToNull(action));
        if (resolved == null || resolved == AuditActionEnum.SUBMIT) {
            // SUBMIT 是组织端提交时长用的动作，不允许从审核接口传进来
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核动作不合法，只能是 APPROVE 或 REJECT");
        }
        return resolved;
    }

    /**
     * 校验审核备注：驳回必填，且不能超过列宽。
     *
     * @param action 审核动作
     * @param remark 备注原文
     * @return 去空白后的备注；通过且未填时返回 null
     * @throws BusinessException 驳回未填理由或备注超长（10001）
     */
    private String resolveRemark(AuditActionEnum action, String remark) {
        String text = trimToNull(remark);
        if (action == AuditActionEnum.REJECT && text == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "驳回时必须填写驳回理由");
        }
        if (text != null && text.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核备注不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }
        return text;
    }

    /**
     * 按状态计数。
     *
     * @param status 状态枚举
     * @return 记录数
     */
    private long countByStatus(DurationStatusEnum status) {
        Long count = serviceDurationMapper.selectCount(Wrappers.<ServiceDuration>lambdaQuery()
                .eq(ServiceDuration::getStatus, status.getCode()));
        return count == null ? 0L : count;
    }

    private static String plainHours(BigDecimal hours) {
        return hours == null ? "0" : hours.stripTrailingZeros().toPlainString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
