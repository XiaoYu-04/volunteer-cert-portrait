package com.vcp.volunteer.support;

import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.vcp.common.util.StringUtils.isBlank;

/**
 * 活动与签到相关的时间字符串解析。
 *
 * <p><b>为什么需要这一层</b>：前端把时间拆成两个输入框提交（{@code date: '2025-03-22'} +
 * {@code time: '09:00'}），库里的 {@code start_time} / {@code end_time} / {@code deadline}
 * 都是单个 TIMESTAMP；签到管理页手工修正时间时提交的又是 {@code '2025-03-22 08:45'} 这种
 * 完整串。解析规则集中在这里，避免每个 Service 各写一套 {@code plusHours} 之类的拼装。
 *
 * <p>解析失败一律抛 {@link ErrorCodeEnum#PARAM_ERROR}（10001），文案带上具体字段值 ——
 * 前端会把 message 原样弹给用户，写「参数不合法」等于没说。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class VolunteerTimeUtils {

    /** 仅日期，与前端 input[type=date] 的提交格式一致 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日期 + 时分，与前端 input[type=time] 的提交格式一致 */
    private static final DateTimeFormatter DATE_TIME_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 日期 + 时分秒，签到管理页的提示格式 */
    private static final DateTimeFormatter DATE_TIME_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 仅时分，活动列表的「开始时间」列用（前端直接渲染 09:00 这种形态） */
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm");

    private VolunteerTimeUtils() {
    }

    /**
     * 取时间里的「时分」，供活动列表的「开始时间」列直接渲染。
     *
     * <p>前端列表页与详情页都是 {@code {{ row.date }} {{ row.time }}} 直接渲染，
     * 传完整时间串过去页面上会多出一截。库里的 start_time 是单个 TIMESTAMP，
     * 所以「日期 / 时分」这一层拆分只能由服务端做。
     *
     * @param value 时间，允许为 null
     * @return HH:mm；入参为 null 时返回 null
     */
    public static String formatTimeOfDay(LocalDateTime value) {
        return value == null ? null : TIME_OF_DAY.format(value);
    }

    /**
     * 合并「活动日期 + 开始时间」为完整时间。
     *
     * @param date 日期串 yyyy-MM-dd，空值返回 null
     * @param time 时间串 HH:mm，可为空，为空时按当天 00:00 处理
     * @return 合并后的时间；date 为空时返回 null
     * @throws BusinessException 格式无法解析（10001）
     */
    public static LocalDateTime toDateTime(String date, String time) {
        if (isBlank(date)) {
            return null;
        }
        String dateText = date.trim();
        String combined = isBlank(time) ? dateText : dateText + " " + time.trim();
        LocalDateTime parsed = parse(combined);
        if (parsed == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "活动时间格式不正确：" + combined);
        }
        return parsed;
    }

    /**
     * 解析报名截止时间。
     *
     * <p><b>只给日期时按当天最后一刻（23:59:59）处理</b>：表单填的是「报名截止日期」，
     * 若取当天 00:00，用户在截止日当天报名会被判为已过期，与直觉相反。
     *
     * @param text 日期串 yyyy-MM-dd，或完整的 yyyy-MM-dd HH:mm[:ss]，空值返回 null
     * @return 截止时间；入参为空时返回 null
     * @throws BusinessException 格式无法解析（10001）
     */
    public static LocalDateTime toDeadline(String text) {
        if (isBlank(text)) {
            return null;
        }
        String value = text.trim();
        LocalDateTime parsed = parse(value);
        if (parsed == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报名截止日期格式不正确：" + value);
        }
        // 「是否只给了日期」必须看**输入串本身**有没有时间部分。
        // 原判据写的是 value.length() <= DATE.toString().length()，而 DateTimeFormatter.toString()
        // 返回的不是模式串，而是形如 "Value(YearOfEra,4,19,EXCEEDS_PAD)'-'Value(MonthOfYear,2)..." 的
        // 描述串（长度 78，不是 10）—— 条件恒真，于是**连完整日期时间的时分秒也被一律抹成 23:59:59**。
        // 后果：deadline 恒为当天最后一刻，`deadline <= start_time` 对「今天开始的活动」永远不成立
        // （建活动/改活动直接 10001），「进行中且可报名」的活动无法用接口造出来。
        // 2026-09-26 由验收实测发现（`docs/待办清单.md` 的 B33）。
        if (value.indexOf(' ') < 0 && value.indexOf('T') < 0) {
            return parsed.toLocalDate().atTime(LocalTime.of(23, 59, 59));
        }
        return parsed;
    }

    /**
     * 解析签到管理里手工填写的时间，支持 yyyy-MM-dd HH:mm 与 yyyy-MM-dd HH:mm:ss。
     *
     * @param text       时间串，空串或 null 返回 null（表示「未签到 / 未签退」）
     * @param fieldLabel 字段名，用于拼接报错文案，如「签到时间」
     * @return 解析结果；入参为空时返回 null
     * @throws BusinessException 格式无法解析（10001）
     */
    public static LocalDateTime toFlexibleDateTime(String text, String fieldLabel) {
        if (isBlank(text)) {
            return null;
        }
        String value = text.trim();
        LocalDateTime parsed = parse(value);
        if (parsed == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    fieldLabel + "格式不正确：" + value + "，应为 2025-03-22 08:45 或 2025-03-22 08:45:00");
        }
        return parsed;
    }

    /**
     * 依次尝试几种常见格式解析。
     *
     * @param text 待解析文本
     * @return 解析结果；全部格式都不匹配时返回 null
     */
    private static LocalDateTime parse(String text) {
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{DATE_TIME_SECOND, DATE_TIME_MINUTE}) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续试下一种格式，全部失败时由调用方抛业务异常
            }
        }
        try {
            return LocalDate.parse(text, DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
