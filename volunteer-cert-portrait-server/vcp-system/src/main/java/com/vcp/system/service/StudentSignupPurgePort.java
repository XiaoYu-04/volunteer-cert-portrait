package com.vcp.system.service;

/**
 * 学生报名清理端口：账号被删除时，清理该学生在活动域（报名 / 签到 / 活动名额计数）的数据。
 *
 * <p><b>为什么需要这个接口（而不是直接改 activity_signup 表）</b>：模块依赖方向是
 * {@code vcp-volunteer → vcp-system}，vcp-system 处在更底层，<b>不能反向依赖
 * vcp-volunteer</b>，碰不到 activity_signup / attendance_record 的实体与 Mapper
 * （项目约定见 CLAUDE.md：禁止反向依赖，跨模块只调对方 Service 接口）。
 * 解法与 {@link OrgLookupPort} / {@link OrgCollegeCountPort} 同一套路：
 * vcp-system 定义端口，vcp-volunteer 提供实现，运行时由 Spring 注入。
 *
 * <p><b>为什么不是「可缺省」的端口</b>：{@code OrgLookupPort} 那种取不到实现只是少一个
 * 展示字段，安静跳过没问题；本端口一旦缺失，删账号就会留下「报名与名额计数仍在参与统计」
 * 的脏数据 —— 那正是待办 B31 的缺陷形态（静默的半个级联比不做级联更难发现）。
 * 因此 {@link com.vcp.system.service.impl.StudentArchivePurger} 用<b>必需注入</b>，
 * 缺实现直接启动失败，而不是运行期悄悄少删一张表。
 *
 * <p><b>事务</b>：实现方不自己开事务边界，方法在调用方（删用户）的事务里执行，
 * 失败会连账号一起回滚。
 */
public interface StudentSignupPurgePort {

    /**
     * 清理指定学生的报名与签到数据。
     *
     * <p>口径（与 {@code sql/09_consistency_check.sql} 第 ⑦ 项一致）：
     * <ul>
     *   <li>该学生名下 {@code deleted = 0} 的报名行一律<b>逻辑删除</b>（{@code deleted = 1}），
     *       原 {@code status} 保持不变；</li>
     *   <li>报名行删完之后，按第 ⑦ 项的<b>同一段谓词</b>（{@code deleted = 0 AND status IN
     *       ('PENDING','APPROVED','COMPLETED')}）<b>重算</b>这些活动已被占用名额数并写回
     *       {@code signed_count}。用重算而不是逐行 -1：同一学生在一个活动下可能有多条报名
     *       （被驳回后又重新报名），逐行减容易多减漏减，重算则天然幂等；</li>
     *   <li>这些报名已生成的签到记录一并作废（逻辑删除）。</li>
     * </ul>
     *
     * <p><b>只置 CANCELED 是不够的</b>：看板「报名总人数」按
     * {@code COUNT(*) FROM activity_signup WHERE deleted = 0} 统计，取消态照样会被数进去，
     * 因此本端口采用逻辑删除。
     *
     * <p><b>幂等</b>：第二次调用时已无 {@code deleted = 0} 的报名行，不做任何事，
     * 不会重复释放名额。
     *
     * @param studentId 学生档案 id（{@code student_info.id}，<b>不是</b> {@code sys_user.id}）
     */
    void purgeByStudentId(Long studentId);
}
