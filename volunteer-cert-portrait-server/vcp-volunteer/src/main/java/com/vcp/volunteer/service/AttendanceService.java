package com.vcp.volunteer.service;

import com.vcp.common.result.PageResult;
import com.vcp.volunteer.dto.AttendanceQuery;
import com.vcp.volunteer.dto.AttendanceUpdateDTO;
import com.vcp.volunteer.vo.AttendanceVO;

/**
 * 签到签退服务。
 *
 * <p>状态与时长都是<b>查询时惰性判定</b>的结果，库里只存原始状态与签到签退时间：
 * 未签到且已过签退窗口的记录对外显示为缺勤（待办 A2），实得时长按签到签退差计算并
 * 受活动预计时长的 1.5 倍封顶（待办 A1）。口径集中在
 * {@link com.vcp.volunteer.support.AttendancePolicy}，本类只负责取数与归属校验。
 */
public interface AttendanceService {

    /**
     * 分页查询签到记录。
     *
     * @param query 筛选条件
     * @return 签到记录分页结果
     */
    PageResult<AttendanceVO> listAttendance(AttendanceQuery query);

    /**
     * 人工修正签到记录（组织管理员 / 学校管理员）。
     *
     * <p>覆盖式提交：前端把空串当作「无该时间」提交，因此空串一律解析成 null 并覆盖原值，
     * 而不是保留原值。现场设备异常漏签就靠这个接口补。
     *
     * @param id  签到记录 id
     * @param dto 目标状态与签到签退时间
     * @throws com.vcp.common.exception.BusinessException 记录不存在（30011）、状态或时间不合法（10001）
     */
    void updateAttendance(Long id, AttendanceUpdateDTO dto);

    /**
     * 学生签到。
     *
     * <p>只能给自己签到（归属取登录态），且必须落在签到窗口内：
     * 活动开始前 30 分钟开放，签退截止时间（活动结束后 30 分钟）后不再开放。
     *
     * @param attendanceId 签到记录 id
     * @throws com.vcp.common.exception.BusinessException 记录不存在或不属于当前学生（30011）、
     *                                                    当前状态不可签到（30012）、不在签到窗口内（10001）
     */
    void signIn(Long attendanceId);

    /**
     * 学生签退。
     *
     * <p>必须先签到，且活动结束后 30 分钟内完成；逾期不再开放签退，
     * 由组织管理员在签到管理里按实际情况修正（不引入定时任务，见待办 A2）。
     *
     * @param attendanceId 签到记录 id
     * @throws com.vcp.common.exception.BusinessException 记录不存在或不属于当前学生（30011）、
     *                                                    未签到（30013）、已过签退窗口（10001）
     */
    void signOut(Long attendanceId);
}
