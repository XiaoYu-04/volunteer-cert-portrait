package com.vcp.volunteer.constant;

import java.math.BigDecimal;

/**
 * 志愿服务活动域的业务常量。
 *
 * <p><b>为什么集中在一处</b>：签到窗口与时长封顶是待办清单 A1、A2 拍板的口径，
 * 也是答辩时最容易被追问的两个数。散在各 Service 里改一处漏一处，集中在这里便于复核与调整。
 *
 * <p><b>注意</b>：{@link #SIGN_OUT_DEADLINE_MINUTES} 同时被
 * {@code resources/mapper/volunteer/AttendanceRecordMapper.xml} 里的惰性缺勤判定使用 ——
 * MyBatis 的 XML 读不到 Java 常量，那边写的是字面量 30 分钟，改这里时必须同步改 XML。
 *
 * <p>本类是纯常量类，不可实例化。
 */
public final class VolunteerConstants {

    /** 签到开放窗口：活动开始前 30 分钟开放签到（待办 A2 已拍板） */
    public static final int SIGN_IN_OPEN_MINUTES = 30;

    /** 签退截止窗口：活动结束后 30 分钟内必须签退（待办 A2 已拍板） */
    public static final int SIGN_OUT_DEADLINE_MINUTES = 30;

    /** 服务时长封顶倍数：最多按活动预计时长的 1.5 倍计（待办 A1 已拍板） */
    public static final BigDecimal HOURS_CAP_RATIO = new BigDecimal("1.5");

    /** 服务时长小数位（单位：小时），与库里的 NUMERIC(10,1) 展示口径一致但更细一位 */
    public static final int HOURS_SCALE = 2;

    /** 秒 → 小时的换算基数，用于「签退时间 − 签到时间」的时长计算 */
    public static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    /** 分类启用：{@code activity_category.status} 是 SMALLINT（1 启用 / 0 禁用），不是英文码 */
    public static final int CATEGORY_STATUS_ACTIVE = 1;

    /** 分类状态码：启用。库里的 1 在 VO 层翻译成该码，与前端 mock 的取值为准 */
    public static final String CATEGORY_STATUS_ACTIVE_CODE = "ACTIVE";

    /** 分类状态码：禁用 */
    public static final String CATEGORY_STATUS_DISABLED_CODE = "DISABLED";

    private VolunteerConstants() {
    }
}
