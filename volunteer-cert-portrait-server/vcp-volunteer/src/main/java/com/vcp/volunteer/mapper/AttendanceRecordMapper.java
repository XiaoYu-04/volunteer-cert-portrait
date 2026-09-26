package com.vcp.volunteer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vcp.volunteer.dto.AttendanceQuery;
import com.vcp.volunteer.entity.AttendanceRecord;
import com.vcp.volunteer.mapper.row.AttendanceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 签到签退 Mapper。
 *
 * <p>单条记录的增删改查由 BaseMapper 提供；列表要 JOIN 报名、活动与学生信息，
 * 用 XML 实现（见 AttendanceRecordMapper.xml）。
 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {

    /**
     * 分页查询签到记录。
     *
     * @param page        分页对象
     * @param query       筛选条件
     * @param scopeOrgId  数据范围：非空时只返回该组织活动的签到记录（组织管理员）
     * @param now         当前时间，由调用方传入：状态筛选要按「签退窗口是否已关闭」惰性判定，
     *                    用应用侧时间而不是数据库的 NOW()，避免库与应用时区不一致时判错
     * @return 分页结果
     */
    IPage<AttendanceRow> selectAttendancePage(IPage<AttendanceRow> page,
                                              @Param("q") AttendanceQuery query,
                                              @Param("scopeOrgId") Long scopeOrgId,
                                              @Param("now") LocalDateTime now);

    /**
     * 分页查询「某个学生本人的」签到记录（学生端自助签到，待办 B24）。
     *
     * <p>与 {@link #selectAttendancePage} 是同一组 JOIN、同一套筛选，只多一条
     * {@code s.student_id = #{studentId}}：学生 id 由登录态给出，<b>不接受前端传入</b>
     * ——否则改一个 id 就能看到别人的签到记录（同待办 B19）。
     *
     * @param page      分页对象
     * @param query     筛选条件（keyword 匹配活动名称、activityId、status）
     * @param studentId 当前登录学生的学生档案 id
     * @param now       当前时间，由调用方传入，理由同 {@link #selectAttendancePage}
     * @return 分页结果
     */
    IPage<AttendanceRow> selectMyAttendancePage(IPage<AttendanceRow> page,
                                                @Param("q") AttendanceQuery query,
                                                @Param("studentId") Long studentId,
                                                @Param("now") LocalDateTime now);

    /**
     * 取单条签到记录（含活动与学生信息），供签到 / 签退 / 人工修正前校验用。
     *
     * <p>签到与签退要判断归属（只能给自己签到）与时间窗口（活动开始前 30 分钟到
     * 结束后 30 分钟），这两项输入都来自活动，所以不能只查 attendance_record 本身。
     *
     * @param id 签到记录 id
     * @return 记录行；不存在或已删除时返回 null
     */
    AttendanceRow selectAttendanceDetail(@Param("id") Long id);

    /**
     * 报名审核通过时把签到记录置回「未签到」并复活。
     *
     * <p>一条报名最多一条签到记录（{@code signup_id} 唯一），而学生取消报名时本模块把该行
     * 逻辑删除（{@code deleted = 1}）。重新报名并再次通过审核时<b>不能 insert 新行</b>，
     * 否则会撞 {@code signup_id} 的唯一约束，只能把原行改回来（与 A7 复用报名行同一类坑）。
     *
     * <p>用原生 SQL 是刻意的：MyBatis-Plus 的逻辑删除会给 {@code deleted = 0} 的更新自动追加
     * 条件，用 BaseMapper 根本改不到已删除的行。
     *
     * @param signupId 报名 id
     * @return 影响行数：1 表示已有记录被复用，0 表示该报名还没有签到记录（调用方应插入）
     */
    @Update("UPDATE attendance_record SET deleted = 0, status = 'NOT_SIGNED', sign_in_time = NULL, "
            + "sign_out_time = NULL, remark = NULL, update_time = NOW() WHERE signup_id = #{signupId}")
    int resetForSignup(@Param("signupId") Long signupId);

    /**
     * 学生取消报名时作废签到记录。
     *
     * <p>用逻辑删除而不是物理删除，原因同 {@link #resetForSignup}：行要留着给重新报名复用。
     * 作废之后该记录不会出现在签到列表与统计里（各查询都带 {@code deleted = 0}），
     * 因此不需要在别处再补「排除已取消报名」的条件。
     *
     * @param signupId 报名 id
     * @return 影响行数
     */
    @Update("UPDATE attendance_record SET deleted = 1, update_time = NOW() "
            + "WHERE signup_id = #{signupId} AND deleted = 0")
    int invalidateBySignupId(@Param("signupId") Long signupId);

    /**
     * 账号被删除时批量作废该学生名下全部签到记录。
     *
     * <p>与 {@link #invalidateBySignupId} 是同一条语义，只是一个报名、一个学生。
     * 学生名下报名可能有多条，逐条调用是 N 次往返；这里一条语句按 {@code signup_id}
     * 子查询收敛。子查询**不过滤** {@code activity_signup.deleted}，因此放在
     * 「逻辑删除报名行」之前或之后调用都成立（销档的调用顺序见
     * {@code StudentSignupPurgePortImpl}）。
     *
     * @param studentId 学生档案 id
     * @return 影响行数
     */
    @Update("UPDATE attendance_record SET deleted = 1, update_time = NOW() "
            + "WHERE deleted = 0 AND signup_id IN ("
            + "       SELECT id FROM activity_signup WHERE student_id = #{studentId})")
    int invalidateByStudentId(@Param("studentId") Long studentId);
}
