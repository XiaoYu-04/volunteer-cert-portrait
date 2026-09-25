package com.vcp.certification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.certification.entity.ServiceDuration;
import com.vcp.certification.mapper.ServiceDurationMapper;
import com.vcp.system.service.StudentDurationPurgePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生服务时长清理端口实现：账号被删除时，把该学生的时长记录一并软删。
 *
 * <p><b>为什么需要这个类</b>：待办 B31 —— 删账号原先只删 {@code sys_user}，
 * 该学生的 {@code service_duration}（含 APPROVED 的时长）留在库里，
 * 继续计入看板的「累计志愿时长」与时长审核统计。清理动作属于本域，
 * 但发起方（vcp-system 的删用户）在依赖链更底层，于是定义端口、由本模块实现。
 *
 * <p><b>与档案软删必须成对</b>：{@code sql/09} 第 ⑧ 项断言
 * 「{@code student_info.total_duration} 合计 == {@code service_duration} 中 APPROVED 合计」
 * （两边都只统计未删除的行）。只软删档案不软删时长，被删账号的时长会继续留在分母里；
 * 只软删时长不软删档案，档案的累计时长就与明细对不上。两者同处一个事务，必须一起做。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentDurationPurgePortImpl implements StudentDurationPurgePort {

    private final ServiceDurationMapper durationMapper;

    /**
     * 逻辑删除指定学生的全部服务时长记录。
     *
     * <p>不区分状态：待提交 / 待审核 / 已通过 / 已驳回一律软删 ——
     * 账号都删了，这几种状态都没有继续留在统计里的理由。
     *
     * <p>{@code duration_audit}（审核流水）不动：它只追加、无 {@code deleted} 列，
     * 记录的是「某次审核确实发生过」这一事实，不该随业务行一起消失。
     *
     * <p>不回退 {@code student_info.total_duration}：权威值只统计未删除的档案，
     * 而档案在同一事务里被软删，回退反而会把「历史累计」这件事抹掉
     * （账号若被恢复，累计时长应当还在）。
     *
     * @param studentId 学生档案 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeByStudentId(Long studentId) {
        if (studentId == null) {
            return;
        }
        int rows = durationMapper.delete(Wrappers.<ServiceDuration>lambdaQuery()
                .eq(ServiceDuration::getStudentId, studentId));
        if (rows > 0) {
            log.info("[销档] 时长域数据已清理。studentId={}, 作废时长记录 {} 条", studentId, rows);
        }
    }
}
