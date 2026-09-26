package com.vcp.volunteer.support;

import com.vcp.common.enums.AttendanceStatusEnum;
import com.vcp.volunteer.constant.VolunteerConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 签到签退的业务口径：状态惰性判定 + 实得时长计算。
 *
 * <p>集中成一个类而不是散在两个 Service 里，是因为「活动列表页看到的签到状态」
 * 与「时长提交页拿到的时长」必须同源，否则两页对不上，且这类对不上极难排查。
 *
 * <h3>状态惰性判定（待办 A2）</h3>
 * <p>活动结束后 30 分钟内必须签退；逾期不引入定时任务，只在查询时惰性判定：
 * <ul>
 *   <li>{@code NOT_SIGNED}（从未签到）且签退窗口已关闭 → 对外显示 {@code ABSENT}（缺勤），时长按 0 计。</li>
 *   <li>{@code SIGNED_IN}（签了到没签退）且窗口已关闭 → <b>保持 SIGNED_IN</b>，时长按活动预计时长计，
 *       由组织管理员在「签到管理」里手动修正为 SIGNED_OUT / ABNORMAL / ABSENT。</li>
 * </ul>
 * <p><b>为什么已签到未签退不判缺勤</b>：待办 A1 明确规定「缺签退时取活动预计时长」，
 * 而缺勤按 0 计，两条口径会直接冲突；把真实到场的学生记成缺勤也不合理。
 * 因此「未签退记 ABSENT」按「未完成签到签退流程（从未签到）」理解，
 * 签到后漏签退的场景保留 SIGNED_IN 并在签到管理页给出修正入口。
 *
 * <h3>实得时长（待办 A1）</h3>
 * <ul>
 *   <li>已签退且有完整签到/签退时间：{@code (签退 − 签到) / 3600}，保留 2 位小数。</li>
 *   <li>缺签退时间、状态为 ABNORMAL：取活动预计时长。</li>
 *   <li>封顶：不超过活动预计时长的 1.5 倍（预计时长为 0 或未填时不封顶）。</li>
 *   <li>未签到 / 缺勤：0。</li>
 * </ul>
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class AttendancePolicy {

    private AttendancePolicy() {
    }

    /**
     * 惰性判定后的对外状态。
     *
     * @param rawStatus       库里的状态码
     * @param activityEndTime 活动结束时间，可为空（为空时不做逾期判定）
     * @param now             当前时间，由调用方传入，避免各层取到不同的「现在」
     * @return 展示用状态；库里的码无法识别时按未签到处理
     */
    public static AttendanceStatusEnum resolveStatus(String rawStatus, LocalDateTime activityEndTime, LocalDateTime now) {
        AttendanceStatusEnum status = AttendanceStatusEnum.of(rawStatus);
        if (status == null) {
            status = AttendanceStatusEnum.NOT_SIGNED;
        }
        if (status == AttendanceStatusEnum.NOT_SIGNED && isSignOutWindowClosed(activityEndTime, now)) {
            return AttendanceStatusEnum.ABSENT;
        }
        return status;
    }

    /**
     * 签退窗口是否已关闭。
     *
     * @param activityEndTime 活动结束时间，为空时视为未关闭
     * @param now             当前时间
     * @return 已过「活动结束 + 30 分钟」时返回 true
     */
    private static boolean isSignOutWindowClosed(LocalDateTime activityEndTime, LocalDateTime now) {
        if (activityEndTime == null || now == null) {
            return false;
        }
        return now.isAfter(activityEndTime.plusMinutes(VolunteerConstants.SIGN_OUT_DEADLINE_MINUTES));
    }

    /**
     * 签到窗口是否开放：活动开始前 30 分钟到签退截止时间之间。
     *
     * @param activityStartTime 活动开始时间，为空时不开放（无法判断窗口）
     * @param activityEndTime   活动结束时间，为空时按开始时间兜底
     * @param now               当前时间
     * @return 可以签到时返回 true
     */
    public static boolean isSignInOpen(LocalDateTime activityStartTime, LocalDateTime activityEndTime, LocalDateTime now) {
        if (activityStartTime == null || now == null) {
            return false;
        }
        LocalDateTime openAt = activityStartTime.minusMinutes(VolunteerConstants.SIGN_IN_OPEN_MINUTES);
        LocalDateTime closeAt = effectiveEndTime(activityStartTime, activityEndTime)
                .plusMinutes(VolunteerConstants.SIGN_OUT_DEADLINE_MINUTES);
        return !now.isBefore(openAt) && !now.isAfter(closeAt);
    }

    /**
     * 签退窗口是否仍开放（用于签退接口）。
     *
     * @param activityStartTime 活动开始时间
     * @param activityEndTime   活动结束时间，为空时按开始时间兜底
     * @param now               当前时间
     * @return 可以签退时返回 true
     */
    public static boolean isSignOutOpen(LocalDateTime activityStartTime, LocalDateTime activityEndTime, LocalDateTime now) {
        if (activityStartTime == null || now == null) {
            return false;
        }
        LocalDateTime closeAt = effectiveEndTime(activityStartTime, activityEndTime)
                .plusMinutes(VolunteerConstants.SIGN_OUT_DEADLINE_MINUTES);
        return !now.isAfter(closeAt);
    }

    /**
     * 计算实得服务时长。
     *
     * @param status          惰性判定后的状态
     * @param signInTime      签到时间，可为空
     * @param signOutTime     签退时间，可为空
     * @param plannedDuration 活动预计时长（小时），可为空或 0
     * @return 实得时长，保留 2 位小数；未签到 / 缺勤返回 0.00
     */
    public static BigDecimal resolveHours(AttendanceStatusEnum status, LocalDateTime signInTime,
                                          LocalDateTime signOutTime, BigDecimal plannedDuration) {
        if (status == null || status == AttendanceStatusEnum.NOT_SIGNED
                || status == AttendanceStatusEnum.ABSENT) {
            return zeroHours();
        }

        BigDecimal planned = normalizePlanned(plannedDuration);
        BigDecimal actual = actualHours(status, signInTime, signOutTime);

        if (actual == null) {
            return planned == null ? zeroHours() : planned.setScale(VolunteerConstants.HOURS_SCALE, RoundingMode.HALF_UP);
        }
        if (planned == null) {
            return actual;
        }
        BigDecimal cap = planned.multiply(VolunteerConstants.HOURS_CAP_RATIO)
                .setScale(VolunteerConstants.HOURS_SCALE, RoundingMode.HALF_UP);
        return actual.min(cap);
    }

    /**
     * 按签到签退时间算实际时长。
     *
     * @param status      状态
     * @param signInTime  签到时间
     * @param signOutTime 签退时间
     * @return 实际时长；时间不完整或先后顺序异常（负数）时返回 null，交由调用方回退到预计时长
     */
    private static BigDecimal actualHours(AttendanceStatusEnum status, LocalDateTime signInTime, LocalDateTime signOutTime) {
        if (status != AttendanceStatusEnum.SIGNED_OUT || signInTime == null || signOutTime == null) {
            return null;
        }
        long seconds = Duration.between(signInTime, signOutTime).getSeconds();
        if (seconds <= 0) {
            return null;
        }
        return BigDecimal.valueOf(seconds)
                .divide(VolunteerConstants.SECONDS_PER_HOUR, VolunteerConstants.HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 归一化活动预计时长。
     *
     * @param plannedDuration 预计时长
     * @return 大于 0 时返回原值，否则返回 null（视为未配置，不参与封顶）
     */
    private static BigDecimal normalizePlanned(BigDecimal plannedDuration) {
        if (plannedDuration == null || plannedDuration.signum() <= 0) {
            return null;
        }
        return plannedDuration;
    }

    /**
     * 活动结束时间兜底：库里允许 end_time 为空，此时按开始时间处理，
     * 使窗口退化为「开始前 30 分钟 ~ 开始后 30 分钟」，而不是变成永久开放。
     *
     * @param startTime 开始时间
     * @param endTime   结束时间，可为空
     * @return 生效的结束时间
     */
    private static LocalDateTime effectiveEndTime(LocalDateTime startTime, LocalDateTime endTime) {
        return endTime == null ? startTime : endTime;
    }

    /**
     * 0 时长的统一表示。
     *
     * @return 0.00
     */
    private static BigDecimal zeroHours() {
        return BigDecimal.ZERO.setScale(VolunteerConstants.HOURS_SCALE, RoundingMode.HALF_UP);
    }
}
