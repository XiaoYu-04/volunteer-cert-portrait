package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.service.StudentDurationPurgePort;
import com.vcp.system.service.StudentSignupPurgePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生档案销档入口：账号被删除时，连带清理该账号在学生域、活动域、时长域的全部数据。
 *
 * <p><b>为什么需要它（待办 B31）</b>：{@code DELETE /api/v1/system/users/{id}} 原先只置
 * {@code sys_user.deleted = 1}，该账号的 {@code student_info} 仍是 {@code deleted = 0}、
 * 报名与时长记录也原样留在库里，于是被删账号<b>继续计入看板</b> ——
 * 实测一轮端到端脚本就会让看板从 1503 学生 / 10719 报名 / 19319.3 小时漂成
 * 1504 / 10720 / 19321.8，而 {@code sql/09} 的 20 项自检<b>抓不到</b>这类漂移
 * （每一张表单看都自洽，是「表与表之间」的引用没跟着删）。
 * 销档收敛到本类一处，与 {@link StudentArchiveRegistrar}（建档）成对，
 * 让「删账号到底要动哪几张表」只有一个落点。
 *
 * <p><b>为什么分三层而不是一条大 SQL</b>：表分属三个模块（档案在 vcp-system、
 * 报名/签到在 vcp-volunteer、时长在 vcp-certification），依赖方向是单向的，
 * vcp-system 碰不到另外两家的表。跨模块动作走两个端口（见
 * {@link StudentSignupPurgePort} / {@link StudentDurationPurgePort}），
 * 每个模块清自己的表，本类只负责编排顺序与档案本身。
 *
 * <p><b>顺序</b>：先清活动域（要读报名状态来释放名额，必须在报名行被逻辑删除之前做），
 * 再清时长域，最后软删档案。三步同处调用方（{@code UserServiceImpl.deleteUser}）的
 * 事务，任何一步抛异常都会整体回滚，不会留下「删了一半」的账号。
 *
 * <p><b>只软删、不物理删</b>：{@code sql/09} 的第 ② / ④ / ⑤ 项是外键悬空检查，
 * 且<b>刻意不过滤 {@code deleted}</b>（外键是物理约束，父行逻辑删除不影响「引用还在」）。
 * 物理删除报名或档案会让这几项立刻报出大量悬空引用，因此一律逻辑删除，行保留、只打标记。
 *
 * <p><b>刻意不做的两件事</b>：
 * <ul>
 *   <li>不删 {@code notification}（发给该账号的通知）：通知按 {@code user_id} 存，
 *       而所有读取路径都以当前登录用户过滤，已删账号的通知没有任何入口能读到；</li>
 *   <li>不删 {@code student_profile}（画像快照）：该表没有 {@code deleted} 列，
 *       且它是整表重算的派生产物，读取侧一律 JOIN {@code student_info} 并带
 *       {@code deleted = 0}（本轮补齐了漏掉这一条的标签分布查询），
 *       档案一软删，快照就不再被任何读路径看见。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentArchivePurger {

    private final StudentInfoMapper studentInfoMapper;

    /**
     * 活动域清理（报名 / 签到 / 名额计数）。
     *
     * <p>必需注入而不是 {@code ObjectProvider.getIfAvailable()}：缺实现时安静跳过，
     * 删账号会退化成 B31 原来的样子且不报任何错。宁可启动失败，也不要静默的半个级联。
     */
    private final StudentSignupPurgePort signupPurgePort;

    /** 时长域清理（service_duration），必需注入的理由同上 */
    private final StudentDurationPurgePort durationPurgePort;

    /**
     * 按账号 id 销档：清理该账号的全部学生数据并逻辑删除档案。
     *
     * <p><b>必须在调用方的事务里执行</b>（与建档相反的操作，理由相同）：
     * 删账号与销档要么一起成功，要么一起回滚。
     *
     * <p>非学生账号（学校 / 组织管理员）没有 {@code student_info} 行，本方法直接返回 ——
     * 这是正常路径，不是异常：管理员本来就不该有档案。
     *
     * @param userId 账号 id（{@code sys_user.id}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void purgeByUserId(Long userId) {
        StudentInfo archive = userId == null ? null : findByUserId(userId);
        if (archive == null) {
            // 管理员账号（或已销过档的账号）：没有档案就没有任何学生域数据要清
            return;
        }

        Long studentId = archive.getId();
        // 顺序不可换：释放 signed_count 需要读报名行当前的状态，必须在报名行被逻辑删除之前完成
        signupPurgePort.purgeByStudentId(studentId);
        durationPurgePort.purgeByStudentId(studentId);
        // 档案最后删：它是另外两张表的定位键（student_id 就是本表主键），先删只是换个顺序，
        // 但放最后能让日志里的「销档」一行同时代表前面两步已经走完
        studentInfoMapper.deleteById(studentId);

        log.info("[销档] 账号删除，学生数据已级联清理。userId={}, studentId={}, studentNo={}",
                userId, studentId, archive.getStudentNo());
    }

    /**
     * 按账号 id 取在用档案。
     *
     * <p>查询自带 {@code @TableLogic} 的 {@code deleted = 0}：已销档的账号查不出来，
     * 重复调用会直接返回，天然幂等。
     *
     * @param userId 账号 id
     * @return 档案；该账号没有在用档案时返回 null
     */
    private StudentInfo findByUserId(Long userId) {
        return studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId)
                .last("LIMIT 1"));
    }
}
