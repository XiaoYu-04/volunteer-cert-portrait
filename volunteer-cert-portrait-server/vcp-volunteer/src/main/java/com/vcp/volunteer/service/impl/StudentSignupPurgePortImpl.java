package com.vcp.volunteer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.system.service.StudentSignupPurgePort;
import com.vcp.volunteer.entity.ActivitySignup;
import com.vcp.volunteer.mapper.ActivitySignupMapper;
import com.vcp.volunteer.mapper.AttendanceRecordMapper;
import com.vcp.volunteer.mapper.VolunteerActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生报名清理端口实现：账号被删除时，把该学生在活动域的痕迹一并作废。
 *
 * <p><b>为什么需要这个类</b>：待办 B31 —— 删账号原先只删 {@code sys_user}，
 * 报名与签到留在库里继续参与统计。清理动作属于本域，但发起方（vcp-system 的删用户）
 * 在依赖链更底层、碰不到本域的表，于是它在 vcp-system 侧定义端口、用必需注入取实现，
 * 由本模块提供实现。套路与 {@code OrgMetricsPortImpl} 一致。
 *
 * <p><b>三步的顺序不能换</b>（见 {@link #purgeByStudentId(Long)} 的注释）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentSignupPurgePortImpl implements StudentSignupPurgePort {

    private final ActivitySignupMapper signupMapper;

    private final AttendanceRecordMapper attendanceMapper;

    private final VolunteerActivityMapper activityMapper;

    /**
     * 清理指定学生的报名、签到与活动名额计数。
     *
     * <p><b>三步顺序的理由</b>：
     * <ol>
     *   <li><b>先作废签到记录</b>：签到表按 {@code signup_id} 定位，子查询只认
     *       {@code activity_signup.id}，与报名行是否已逻辑删除无关，放哪一步都对；
     *       但放在最前面，是为了让「学生名下有多少条报名」这件事在日志里只查一次；</li>
     *   <li><b>再逻辑删除报名行</b>：一条 {@code UPDATE ... SET deleted = 1}（MyBatis-Plus
     *       的 {@code @TableLogic} 生成），覆盖该学生全部报名；
     *       原 {@code status} 一律保持原值 —— 它是审计事实（「当时确实是 APPROVED / COMPLETED」），
     *       把它改写成 CANCELED 会把「账号被删」伪装成「学生主动取消」；</li>
     *   <li><b>最后按口径重算名额计数</b>：必须在报名行被软删之后，
     *       用的是 {@code sql/09} 第 ⑦ 项的同一段谓词，重算完第 ⑦ 项必然为 0。</li>
     * </ol>
     *
     * <p><b>为什么不能只把报名改成 CANCELED</b>（本类不采用那条口径的原因）：
     * 看板「报名总人数」的口径是 {@code COUNT(*) FROM activity_signup WHERE deleted = 0}
     * （{@code AnalyticsMapper} 的核心指标查询），<b>CANCELED 行照样会被数进去</b> ——
     * 只改状态的话，删一个账号看板仍会 +1，待办 B31 的现象一点没治。
     * 逻辑删除才能让这条报名从所有聚合里消失。
     *
     * <p><b>幂等</b>：第二次调用时 {@code deleted = 0} 的报名已不存在，
     * 逻辑删除影响 0 行、不触发重算，不会重复扣名额。
     *
     * @param studentId 学生档案 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeByStudentId(Long studentId) {
        if (studentId == null) {
            return;
        }

        int attendance = attendanceMapper.invalidateByStudentId(studentId);

        int signups = signupMapper.delete(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getStudentId, studentId));

        int activities = signups > 0 ? activityMapper.refreshSignedCountByStudent(studentId) : 0;

        if (signups > 0) {
            log.info("[销档] 活动域数据已清理。studentId={}, 作废报名 {} 条，作废签到 {} 条，重算名额的活动 {} 场",
                    studentId, signups, attendance, activities);
        }
    }
}
