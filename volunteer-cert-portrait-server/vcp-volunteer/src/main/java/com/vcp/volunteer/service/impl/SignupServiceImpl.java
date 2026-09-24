package com.vcp.volunteer.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.ActivityStatusEnum;
import com.vcp.common.enums.AttendanceStatusEnum;
import com.vcp.common.enums.AuditActionEnum;
import com.vcp.common.enums.NotificationTypeEnum;
import com.vcp.common.enums.SignupStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.system.service.NotificationService;
import com.vcp.volunteer.dto.SignupAuditDTO;
import com.vcp.volunteer.dto.SignupCreateDTO;
import com.vcp.volunteer.dto.SignupQuery;
import com.vcp.volunteer.entity.ActivitySignup;
import com.vcp.volunteer.entity.AttendanceRecord;
import com.vcp.volunteer.entity.VolunteerActivity;
import com.vcp.volunteer.mapper.ActivitySignupMapper;
import com.vcp.volunteer.mapper.AttendanceRecordMapper;
import com.vcp.volunteer.mapper.VolunteerActivityMapper;
import com.vcp.volunteer.mapper.row.SignupAuditRow;
import com.vcp.volunteer.mapper.row.SignupRow;
import com.vcp.volunteer.service.SignupService;
import com.vcp.volunteer.support.StudentIdentityUtils;
import com.vcp.volunteer.vo.SignupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 活动报名服务实现。
 *
 * <h3>名额计数（signed_count）的口径</h3>
 * <p><b>提交报名时就占名额</b>，用
 * {@code UPDATE volunteer_activity SET signed_count = signed_count + 1 WHERE id = ? AND (max_count <= 0 OR signed_count < max_count)}
 * 一条语句完成「判断 + 占位」，影响行数为 0 即名额已满（待办 A3 的结论）。
 * 不先查后写：库里没有 version 列，乐观锁用不了，先查后写必然超卖。
 *
 * <p>因此：<b>审核通过不再加计数</b>（同一份报名会被数两次），驳回与取消各释放一个名额。
 * 这个口径与前端 mock 完全一致（提交报名 enrolled + 1、驳回与取消 - 1、通过不变），
 * 也是 POST /signups 能返回 30006「名额已满」的前提。
 *
 * <p><b>与演示脚本口径一致</b>：04_demo_data.sql 与 07_demo_scale.sql 已按同一口径
 * （「未取消未驳回的报名」，即 PENDING + APPROVED + COMPLETED）回填 signed_count，
 * 脚本与实现之间不再有差异（待办 B20-1 的结论）。07_demo_scale.sql 的新活动名额下限是 46，
 * 高于该脚本第五节的报名数上限 45，两侧共同保证 signed_count 不超过 max_count。
 * 冗余计数在极端情况下仍可能与明细漂移，是否报满只看这个计数与 max_count 的比较。
 *
 * <h3>数据范围</h3>
 * <p>一律由登录态决定：学生只看自己的报名，组织管理员只看本组织活动的报名，
 * 学校管理员看全量。前端传来的 studentId / orgId 不能作为范围依据（待办 B19）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupServiceImpl implements SignupService {

    /** 通知的发布方缺省值，组织名取不到时使用 */
    private static final String NOTIFICATION_SOURCE = "志愿组织";

    private final ActivitySignupMapper signupMapper;

    private final VolunteerActivityMapper activityMapper;

    private final AttendanceRecordMapper attendanceMapper;

    private final NotificationService notificationService;

    @Override
    public PageResult<SignupVO> listSignups(SignupQuery query) {
        SignupQuery condition = query == null ? new SignupQuery() : query;

        Long scopeStudentId = null;
        Long scopeOrgId = null;
        if (AuthUtils.isStudent()) {
            scopeStudentId = StudentIdentityUtils.requireCurrentStudentId();
            // 学生只能看自己的：前端传的 studentId 一律清掉，否则改一个 id 就能读别人的报名
            condition.setStudentId(null);
            condition.setOrgId(null);
        } else if (AuthUtils.isOrgAdmin()) {
            scopeOrgId = AuthUtils.getOrgId();
            condition.setOrgId(null);
            if (scopeOrgId == null) {
                return PageResult.empty();
            }
        }

        IPage<SignupRow> page = signupMapper.selectSignupPage(
                PageUtils.<SignupRow>toPage(condition), condition, scopeStudentId, scopeOrgId);
        return PageUtils.page(page, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSignup(SignupCreateDTO dto) {
        if (dto == null || dto.getActivityId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择要报名的活动");
        }
        Long studentId = StudentIdentityUtils.requireCurrentStudentId();
        VolunteerActivity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (!ActivityStatusEnum.PUBLISHED.getCode().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_OPEN);
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getDeadline() != null && now.isAfter(activity.getDeadline())) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_OPEN,
                    "报名已于 " + DateTimeUtils.formatDateTime(activity.getDeadline()) + " 截止");
        }

        ActivitySignup existing = signupMapper.selectOne(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, activity.getId())
                .eq(ActivitySignup::getStudentId, studentId));
        if (existing != null && !SignupStatusEnum.CANCELED.getCode().equals(existing.getStatus())) {
            // 已取消的记录允许重新报名（复用同一行）；其余状态（含已驳回）按重复报名处理
            throw new BusinessException(ErrorCodeEnum.SIGNUP_DUPLICATE);
        }

        // 原子占位：0 行表示名额已满或活动已被删除，这里按名额已满提示（活动刚被删是极小概率）
        if (activityMapper.increaseSignedCount(activity.getId()) == 0) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_FULL);
        }

        if (existing == null) {
            ActivitySignup signup = new ActivitySignup();
            signup.setActivityId(activity.getId());
            signup.setStudentId(studentId);
            signup.setSignupTime(now);
            signup.setStatus(SignupStatusEnum.PENDING.getCode());
            signup.setReason(trimToNull(dto.getReason()));
            signupMapper.insert(signup);
            log.info("[活动报名] 提交报名。signupId={}, activityId={}, studentId={}",
                    signup.getId(), activity.getId(), studentId);
            return signup.getId();
        }

        // 取消过的那一行改回待审核：表上有 uk_activity_student，软删除后重新报名会撞唯一键（待办 A7）
        LocalDateTime cancelTime = now;
        LambdaUpdateWrapper<ActivitySignup> update = Wrappers.lambdaUpdate();
        update.eq(ActivitySignup::getId, existing.getId());
        update.set(ActivitySignup::getStatus, SignupStatusEnum.PENDING.getCode());
        update.set(ActivitySignup::getSignupTime, cancelTime);
        update.set(ActivitySignup::getReason, trimToNull(dto.getReason()));
        update.set(ActivitySignup::getAuditRemark, null);
        update.set(ActivitySignup::getAuditUserId, null);
        update.set(ActivitySignup::getAuditTime, null);
        update.set(ActivitySignup::getUpdateTime, cancelTime);
        signupMapper.update(null, update);
        log.info("[活动报名] 重新报名，复用原记录。signupId={}, activityId={}, studentId={}",
                existing.getId(), activity.getId(), studentId);
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditSignup(Long id, SignupAuditDTO dto) {
        SignupAuditRow row = id == null ? null : signupMapper.selectAuditRow(id);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND);
        }
        requireAuditable(row);
        if (!SignupStatusEnum.PENDING.getCode().equals(row.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_ALREADY_AUDITED);
        }
        AuditActionEnum action = dto == null ? null : AuditActionEnum.of(dto.getAction());
        if (action == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核动作不合法");
        }
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        if (action == AuditActionEnum.REJECT && remark.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "驳回时必须填写理由");
        }

        boolean approved = action == AuditActionEnum.APPROVE;
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ActivitySignup> update = Wrappers.lambdaUpdate();
        update.eq(ActivitySignup::getId, row.getSignupId());
        // 带上原状态做条件更新：并发下只有一个请求能改成功，另一个按「已审核」提示
        update.eq(ActivitySignup::getStatus, SignupStatusEnum.PENDING.getCode());
        update.set(ActivitySignup::getStatus,
                approved ? SignupStatusEnum.APPROVED.getCode() : SignupStatusEnum.REJECTED.getCode());
        update.set(ActivitySignup::getAuditRemark, remark.isEmpty() ? null : remark);
        update.set(ActivitySignup::getAuditUserId, AuthUtils.getUserId());
        update.set(ActivitySignup::getAuditTime, now);
        update.set(ActivitySignup::getUpdateTime, now);
        if (signupMapper.update(null, update) == 0) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_ALREADY_AUDITED);
        }

        if (approved) {
            // 名额在提交报名时就已经占用，这里不再加计数，否则同一份报名会被数两次
            ensureAttendanceRecord(row.getSignupId());
            sendApprovedNotification(row);
        } else {
            activityMapper.decreaseSignedCount(row.getActivityId());
        }
        log.info("[活动报名] 审核完成。signupId={}, activityId={}, action={}",
                row.getSignupId(), row.getActivityId(), action.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSignup(Long id) {
        ActivitySignup signup = id == null ? null : signupMapper.selectById(id);
        if (signup == null) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND);
        }
        Long studentId = StudentIdentityUtils.requireCurrentStudentId();
        if (!studentId.equals(signup.getStudentId())) {
            // 不是自己的报名：按不存在处理，不泄露别人报名记录的存在性
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND);
        }
        SignupStatusEnum current = SignupStatusEnum.of(signup.getStatus());
        if (current == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报名状态不合法，请先修正数据");
        }
        switch (current) {
            case COMPLETED -> throw new BusinessException(ErrorCodeEnum.SIGNUP_COMPLETED);
            case CANCELED -> {
                // 重复点击「取消」：已经是取消态，直接成功返回，不再释放名额
                return;
            }
            case REJECTED -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该报名已被驳回，无需取消");
            case PENDING, APPROVED -> {
                // 允许取消，继续往下走
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ActivitySignup> update = Wrappers.lambdaUpdate();
        update.eq(ActivitySignup::getId, signup.getId());
        update.eq(ActivitySignup::getStatus, current.getCode());
        update.set(ActivitySignup::getStatus, SignupStatusEnum.CANCELED.getCode());
        update.set(ActivitySignup::getUpdateTime, now);
        if (signupMapper.update(null, update) == 0) {
            // 并发下已被另一个请求取消，名额也已经释放过，这里不再重复释放
            return;
        }
        activityMapper.decreaseSignedCount(signup.getActivityId());
        // 已通过审核的报名会带一条签到记录：作废它（保留行，重新报名通过审核时复用）
        attendanceMapper.invalidateBySignupId(signup.getId());
        log.info("[活动报名] 取消报名。signupId={}, activityId={}, studentId={}",
                signup.getId(), signup.getActivityId(), studentId);
    }

    /**
     * 校验当前用户可审核这条报名。
     *
     * <p>不在本组织范围内的一律按「报名不存在」处理：前端把 20003 当登录失效，
     * 用它表达数据范围会把人踢下线，也会暴露这条报名确实存在。
     *
     * @param row 审核上下文
     */
    private void requireAuditable(SignupAuditRow row) {
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId != null && orgId.equals(row.getActivityOrgId())) {
                return;
            }
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND);
        }
        if (AuthUtils.isStudent()) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND);
        }
        // 学校管理员可审核任意组织的报名
    }

    /**
     * 审核通过时保证有一条签到记录（未签到）。
     *
     * <p>一条报名最多一条签到记录（signup_id 唯一），学生取消报名时本模块把该行逻辑删除，
     * 重新报名并再次通过时必须复用原行（{@code resetForSignup} 会连删除标记一起改回来），
     * 直接 insert 会撞唯一约束。
     *
     * @param signupId 报名 id
     */
    private void ensureAttendanceRecord(Long signupId) {
        if (attendanceMapper.resetForSignup(signupId) > 0) {
            return;
        }
        AttendanceRecord record = new AttendanceRecord();
        record.setSignupId(signupId);
        record.setStatus(AttendanceStatusEnum.NOT_SIGNED.getCode());
        attendanceMapper.insert(record);
    }

    /**
     * 审核通过后通知学生。
     *
     * <p>收件人取 student_info.user_id（报名表存的是学生档案 id，不是账号 id）。
     *
     * @param row 审核上下文
     */
    private void sendApprovedNotification(SignupAuditRow row) {
        if (row.getStudentUserId() == null) {
            return;
        }
        String content = "你报名的活动「%s」已通过审核，请在活动开始前 30 分钟内到场签到，活动结束后 30 分钟内签退。"
                .formatted(row.getActivityTitle());
        notificationService.createForUser(row.getStudentUserId(), "报名已通过", content,
                NotificationTypeEnum.SIGNUP.getCode(), row.getActivityOrgName());
    }

    /**
     * 行对象到 VO：时间在服务端格式化，前端直接渲染。
     *
     * @param row 查询行
     * @return 报名 VO
     */
    private SignupVO toVO(SignupRow row) {
        SignupVO vo = new SignupVO();
        vo.setId(row.getId());
        vo.setActivityId(row.getActivityId());
        vo.setActivityTitle(row.getActivityTitle());
        vo.setActivityDate(DateTimeUtils.formatDate(row.getActivityStartTime()));
        vo.setActivityHours(row.getActivityDuration());
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setStudentNo(row.getStudentNo());
        vo.setCollege(row.getCollege());
        vo.setStatus(row.getStatus());
        vo.setAppliedAt(DateTimeUtils.formatDateTime(row.getSignupTime()));
        vo.setReason(row.getReason());
        // 审核备注只在驳回时作为「驳回理由」返回，通过时的备注不往这一列上放
        vo.setRejectReason(SignupStatusEnum.REJECTED.getCode().equals(row.getStatus())
                ? row.getAuditRemark()
                : null);
        return vo;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
