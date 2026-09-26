package com.vcp.volunteer.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.AttendanceStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.volunteer.constant.VolunteerConstants;
import com.vcp.volunteer.dto.AttendanceQuery;
import com.vcp.volunteer.dto.AttendanceUpdateDTO;
import com.vcp.volunteer.entity.AttendanceRecord;
import com.vcp.volunteer.mapper.AttendanceRecordMapper;
import com.vcp.volunteer.mapper.row.AttendanceRow;
import com.vcp.volunteer.service.AttendanceService;
import com.vcp.volunteer.support.AttendancePolicy;
import com.vcp.volunteer.support.StudentIdentityUtils;
import com.vcp.volunteer.support.VolunteerTimeUtils;
import com.vcp.volunteer.vo.AttendanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 签到签退服务实现。
 *
 * <p><b>状态与时长都是查出来的，不是存出来的</b>：库里只存原始状态与签到签退时间，
 * 「未签到但已过签退窗口」在查询时按缺勤展示（待办 A2），实得时长按签到签退差计算并
 * 受活动预计时长 1.5 倍封顶（待办 A1）。规则集中在
 * {@link AttendancePolicy}，本类只负责取数、归属校验与落库。
 *
 * <p><b>归属校验用「不存在」而不是「无权限」</b>：前端把 20003 当登录失效，
 * 用它表达数据范围会把人踢下线，也会泄露别人的签到记录确实存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRecordMapper attendanceMapper;

    @Override
    public PageResult<AttendanceVO> listAttendance(AttendanceQuery query) {
        AttendanceQuery condition = query == null ? new AttendanceQuery() : query;

        Long scopeOrgId = null;
        if (AuthUtils.isOrgAdmin()) {
            scopeOrgId = AuthUtils.getOrgId();
            if (scopeOrgId == null) {
                return PageResult.empty();
            }
        } else if (!AuthUtils.isSchoolAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
        // 数据范围只看登录态：前端传的 orgId 一律忽略（XML 里也没有这个筛选条件）
        condition.setOrgId(null);

        // 「现在」在 Service 取一次并往下传：列表里每行都要做惰性判定，
        // 各层各取一次时间会出现同一页里判定基准不一致的诡异现象
        LocalDateTime now = LocalDateTime.now();
        IPage<AttendanceRow> page = attendanceMapper.selectAttendancePage(
                PageUtils.<AttendanceRow>toPage(condition), condition, scopeOrgId, now);
        return PageUtils.page(page, row -> toVO(row, now));
    }

    @Override
    public PageResult<AttendanceVO> listMyAttendance(AttendanceQuery query) {
        AttendanceQuery condition = query == null ? new AttendanceQuery() : query;
        // 数据范围只看登录态：前端传的 studentId / orgId 一律忽略（同 listAttendance 的口径）。
        // studentId 单独作为入参下传，避免与筛选条件里的同名字段混淆
        condition.setStudentId(null);
        condition.setOrgId(null);
        Long studentId = StudentIdentityUtils.requireCurrentStudentId();

        // 与列表同理：一页里每行都要做惰性判定与窗口判定，「现在」只能取一次
        LocalDateTime now = LocalDateTime.now();
        IPage<AttendanceRow> page = attendanceMapper.selectMyAttendancePage(
                PageUtils.<AttendanceRow>toPage(condition), condition, studentId, now);
        return PageUtils.page(page, row -> toVO(row, now));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAttendance(Long id, AttendanceUpdateDTO dto) {
        AttendanceRow row = id == null ? null : attendanceMapper.selectAttendanceDetail(id);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
        requireManageable(row);
        if (dto == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请提交要更新的签到信息");
        }
        AttendanceStatusEnum status = dto.getStatus() == null ? null : AttendanceStatusEnum.of(dto.getStatus().trim());
        if (status == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "签到状态不合法");
        }
        // 覆盖式：空串解析成 null 并写回，表示「未签到 / 未签退」（前端就是这么提交的）
        LocalDateTime signInTime = VolunteerTimeUtils.toFlexibleDateTime(dto.getSignInAt(), "签到时间");
        LocalDateTime signOutTime = VolunteerTimeUtils.toFlexibleDateTime(dto.getSignOutAt(), "签退时间");
        if (status == AttendanceStatusEnum.SIGNED_IN && signInTime == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态置为已签到时必须填写签到时间");
        }
        if (status == AttendanceStatusEnum.SIGNED_OUT && signOutTime == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态置为已签退时必须填写签退时间");
        }
        if (signInTime != null && signOutTime != null && signOutTime.isBefore(signInTime)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "签退时间不能早于签到时间");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AttendanceRecord> update = Wrappers.lambdaUpdate();
        update.eq(AttendanceRecord::getId, row.getId());
        update.set(AttendanceRecord::getStatus, status.getCode());
        update.set(AttendanceRecord::getSignInTime, signInTime);
        update.set(AttendanceRecord::getSignOutTime, signOutTime);
        update.set(AttendanceRecord::getUpdateTime, now);
        attendanceMapper.update(null, update);

        log.info("[签到签退] 人工修正签到记录。attendanceId={}, status={}", row.getId(), status.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signIn(Long attendanceId) {
        AttendanceRow row = attendanceId == null ? null : attendanceMapper.selectAttendanceDetail(attendanceId);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
        requireOwnRecord(row);

        LocalDateTime now = LocalDateTime.now();
        AttendanceStatusEnum status = AttendancePolicy.resolveStatus(row.getStatus(), row.getActivityEndTime(), now);
        if (status != AttendanceStatusEnum.NOT_SIGNED && status != AttendanceStatusEnum.ABSENT) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_CANNOT_SIGN_IN);
        }
        if (!AttendancePolicy.isSignInOpen(row.getActivityStartTime(), row.getActivityEndTime(), now)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, signInWindowMessage(row, now));
        }

        LambdaUpdateWrapper<AttendanceRecord> update = Wrappers.lambdaUpdate();
        update.eq(AttendanceRecord::getId, row.getId());
        update.set(AttendanceRecord::getStatus, AttendanceStatusEnum.SIGNED_IN.getCode());
        update.set(AttendanceRecord::getSignInTime, now);
        update.set(AttendanceRecord::getUpdateTime, now);
        attendanceMapper.update(null, update);

        log.info("[签到签退] 签到。attendanceId={}, activityId={}, studentId={}",
                row.getId(), row.getActivityId(), row.getStudentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signOut(Long attendanceId) {
        AttendanceRow row = attendanceId == null ? null : attendanceMapper.selectAttendanceDetail(attendanceId);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
        requireOwnRecord(row);

        AttendanceStatusEnum status = AttendanceStatusEnum.of(row.getStatus());
        if (status != AttendanceStatusEnum.SIGNED_IN) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_CANNOT_SIGN_OUT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!AttendancePolicy.isSignOutOpen(row.getActivityStartTime(), row.getActivityEndTime(), now)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "签退时间已过（活动结束后 " + VolunteerConstants.SIGN_OUT_DEADLINE_MINUTES
                            + " 分钟内必须签退），请联系组织管理员在签到管理中修正");
        }

        LambdaUpdateWrapper<AttendanceRecord> update = Wrappers.lambdaUpdate();
        update.eq(AttendanceRecord::getId, row.getId());
        update.set(AttendanceRecord::getStatus, AttendanceStatusEnum.SIGNED_OUT.getCode());
        update.set(AttendanceRecord::getSignOutTime, now);
        update.set(AttendanceRecord::getUpdateTime, now);
        attendanceMapper.update(null, update);

        log.info("[签到签退] 签退。attendanceId={}, activityId={}, studentId={}",
                row.getId(), row.getActivityId(), row.getStudentId());
    }

    /**
     * 校验当前用户可管理这条签到记录（组织管理员限本组织，学校管理员不限）。
     *
     * @param row 签到记录行
     */
    private void requireManageable(AttendanceRow row) {
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            // 签到记录本身不带 org_id，组织范围只能经 signup -> activity 反查（待办 B16）
            if (orgId != null && orgId.equals(row.getActivityOrgId())) {
                return;
            }
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
        if (AuthUtils.isStudent()) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
        // 学校管理员可修正任意组织的签到记录
    }

    /**
     * 签到 / 签退只能给自己操作。
     *
     * @param row 签到记录行
     */
    private void requireOwnRecord(AttendanceRow row) {
        Long studentId = StudentIdentityUtils.requireCurrentStudentId();
        if (!studentId.equals(row.getStudentId())) {
            throw new BusinessException(ErrorCodeEnum.ATTENDANCE_NOT_FOUND);
        }
    }

    /**
     * 签到窗口不在开放时段时的提示文案。
     *
     * <p>区分「还没到时间」与「已经过了」：两种情况的处理方式完全不同
     * （前者等一会再来，后者要找组织管理员补签），一句「无法签到」等于没说。
     *
     * @param row 签到记录行
     * @param now 当前时间
     * @return 提示文案
     */
    private static String signInWindowMessage(AttendanceRow row, LocalDateTime now) {
        LocalDateTime startTime = row.getActivityStartTime();
        if (startTime != null && now.isBefore(startTime.minusMinutes(VolunteerConstants.SIGN_IN_OPEN_MINUTES))) {
            return "签到尚未开放，活动开始前 " + VolunteerConstants.SIGN_IN_OPEN_MINUTES + " 分钟才可签到";
        }
        return "签到时间已过，请联系组织管理员在签到管理中修正";
    }

    /**
     * 行对象到 VO：状态做惰性判定，时长按 A1 的口径算出来。
     *
     * @param row 查询行
     * @param now 当前时间，整页共用同一个基准
     * @return 签到记录 VO
     */
    private AttendanceVO toVO(AttendanceRow row, LocalDateTime now) {
        AttendanceStatusEnum status = AttendancePolicy.resolveStatus(row.getStatus(), row.getActivityEndTime(), now);
        AttendanceVO vo = new AttendanceVO();
        vo.setId(row.getId());
        vo.setSignupId(row.getSignupId());
        vo.setActivityId(row.getActivityId());
        vo.setActivityTitle(row.getActivityTitle());
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setStudentNo(row.getStudentNo());
        vo.setCollege(row.getCollege());
        vo.setStatus(status.getCode());
        vo.setSignInAt(DateTimeUtils.formatDateTime(row.getSignInTime()));
        vo.setSignOutAt(DateTimeUtils.formatDateTime(row.getSignOutTime()));
        vo.setActivityStartAt(DateTimeUtils.formatDateTime(row.getActivityStartTime()));
        vo.setActivityEndAt(DateTimeUtils.formatDateTime(row.getActivityEndTime()));
        vo.setCanSignIn(canSignIn(row, status, now));
        vo.setCanSignOut(canSignOut(row, status, now));
        vo.setHours(AttendancePolicy.resolveHours(
                status, row.getSignInTime(), row.getSignOutTime(), row.getActivityDuration()));
        return vo;
    }

    /**
     * 当前是否可签到：状态允许且签到窗口开放。
     *
     * <p>判定条件与 {@link #signIn(Long)} 的准入逐条对应（惰性判定后状态为未签到或已判缺勤，
     * 且 {@link AttendancePolicy#isSignInOpen} 为真）。这样前端按钮的显隐与接口的实际受理
     * 同源，不会出现「按钮能点、点了必然报错」；窗口时间到点后由前端重新拉一次列表刷新。
     *
     * @param row    查询行
     * @param status 惰性判定后的状态
     * @param now    当前时间
     * @return 可签到时返回 true
     */
    private static boolean canSignIn(AttendanceRow row, AttendanceStatusEnum status, LocalDateTime now) {
        boolean statusAllowed = status == AttendanceStatusEnum.NOT_SIGNED
                || status == AttendanceStatusEnum.ABSENT;
        return statusAllowed
                && AttendancePolicy.isSignInOpen(row.getActivityStartTime(), row.getActivityEndTime(), now);
    }

    /**
     * 当前是否可签退：已签到且签退窗口未关闭。
     *
     * <p>与 {@link #signOut(Long)} 的准入对应。注意「签了到但窗口已过」是待办 A2 刻意保留的
     * {@code SIGNED_IN}（不判缺勤），这类记录这里返回 false，改由组织管理员人工修正。
     *
     * @param row    查询行
     * @param status 惰性判定后的状态
     * @param now    当前时间
     * @return 可签退时返回 true
     */
    private static boolean canSignOut(AttendanceRow row, AttendanceStatusEnum status, LocalDateTime now) {
        return status == AttendanceStatusEnum.SIGNED_IN
                && AttendancePolicy.isSignOutOpen(row.getActivityStartTime(), row.getActivityEndTime(), now);
    }

}
