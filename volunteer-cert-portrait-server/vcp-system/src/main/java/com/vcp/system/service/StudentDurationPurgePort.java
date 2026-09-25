package com.vcp.system.service;

/**
 * 学生服务时长清理端口：账号被删除时，清理该学生在时长认证域的数据。
 *
 * <p>与 {@link StudentSignupPurgePort} 同一套路（vcp-system 定义端口、vcp-certification
 * 提供实现），也同样是<b>必需注入</b>：时长是看板「累计志愿时长」与时长审核统计的输入，
 * 漏删会让已删账号继续计入统计（待办 B31）。
 *
 * <p>拆成两个端口而不是一个，是为了让每个模块只清理自己域的表：报名 / 签到归
 * vcp-volunteer，服务时长归 vcp-certification，两边各自实现、各自演进，
 * vcp-system 只负责在删账号时把两个动作串起来。
 */
public interface StudentDurationPurgePort {

    /**
     * 清理指定学生的服务时长记录（逻辑删除）。
     *
     * <p>只软删 {@code service_duration}，<b>不动 {@code duration_audit}</b>：
     * 后者是审核流水（只追加、无 {@code deleted} 列），记录「某次审核确实发生过」这一事实，
     * 不该随业务行一起消失 —— 与项目对流水/日志表的既有约定一致。
     *
     * <p>累计时长不需要回退：权威值是 {@code student_info.total_duration}，
     * 而档案在同一事务里被逻辑删除，第 ⑧ 项自检只比对未删除的档案，不会因此报违规。
     *
     * <p><b>幂等</b>：第二次调用时已无 {@code deleted = 0} 的记录，影响行数为 0。
     *
     * @param studentId 学生档案 id（{@code student_info.id}）
     */
    void purgeByStudentId(Long studentId);
}
