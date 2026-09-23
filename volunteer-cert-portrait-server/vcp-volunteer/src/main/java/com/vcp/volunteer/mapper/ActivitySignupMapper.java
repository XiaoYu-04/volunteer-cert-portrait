package com.vcp.volunteer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vcp.volunteer.dto.SignupQuery;
import com.vcp.volunteer.entity.ActivitySignup;
import com.vcp.volunteer.mapper.row.SignupAuditRow;
import com.vcp.volunteer.mapper.row.SignupRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 活动报名 Mapper。
 */
@Mapper
public interface ActivitySignupMapper extends BaseMapper<ActivitySignup> {

    /**
     * 分页查询报名记录。
     *
     * @param page            分页对象
     * @param query           筛选条件
     * @param scopeStudentId  数据范围：非空时只返回该学生的报名（学生角色）
     * @param scopeOrgId      数据范围：非空时只返回该组织活动的报名（组织管理员）
     * @return 分页结果
     */
    IPage<SignupRow> selectSignupPage(IPage<SignupRow> page,
                                      @Param("q") SignupQuery query,
                                      @Param("scopeStudentId") Long scopeStudentId,
                                      @Param("scopeOrgId") Long scopeOrgId);

    /**
     * 取审核一条报名所需的上下文。
     *
     * @param signupId 报名 id
     * @return 上下文行；报名不存在或已删除时返回 null
     */
    SignupAuditRow selectAuditRow(@Param("signupId") Long signupId);

    /**
     * 活动结束时把「确实到场」的报名置为已完成。
     *
     * <p>口径：只有存在签到记录且签到状态属于 SIGNED_IN / SIGNED_OUT / ABNORMAL 的报名才算
     * 「活动结束且已参加」（COMPLETED 的字典释义），从未签到的缺勤记录保持 APPROVED，
     * 免得把没到场的学生也算进画像的参与次数。
     *
     * @param activityId 活动 id
     * @return 影响行数
     */
    @Update("UPDATE activity_signup s SET status = 'COMPLETED', update_time = NOW() "
            + "WHERE s.activity_id = #{activityId} AND s.deleted = 0 AND s.status = 'APPROVED' "
            + "AND EXISTS (SELECT 1 FROM attendance_record r WHERE r.signup_id = s.id AND r.deleted = 0 "
            + "AND r.status IN ('SIGNED_IN', 'SIGNED_OUT', 'ABNORMAL'))")
    int completeAttendedSignups(@Param("activityId") Long activityId);
}
