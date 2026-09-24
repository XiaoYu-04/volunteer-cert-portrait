package com.vcp.certification.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vcp.certification.vo.DurationVO;
import com.vcp.common.enums.SignupStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 时长认证域的跨表读写 Mapper（只读拼装 + 一条累计时长累加）。
 *
 * <p><b>为什么把跨域访问集中在这一个文件里</b>：前端契约要求时长列表返回学生姓名/学号/学院、
 * 活动名称/类型、提交组织名称、审核人姓名，而这些字段分散在 student_info、sys_user、
 * volunteer_activity、activity_category、org_info 五张表上。它们分属 vcp-system 与
 * vcp-volunteer 域，本模块只能通过对方的 Service 接口取用，但：
 * <ul>
 *   <li>vcp-volunteer 与本模块并行开发、尚未落地，其 Service 接口与实体类名都还不存在，
 *       现在引用会直接编译不过；</li>
 *   <li>vcp-system 的 StudentService 只有查询接口（设计如此：档案由学校管理员补录），
 *       没有任何写累计时长的入口，而审核通过必须累加 student_info.total_duration。</li>
 * </ul>
 * 因此这里用只读 JOIN 拼出前端字段、用参数化 UPDATE 做累计时长的原子累加，
 * 把「跨域访问」全部收在本类里，便于将来 vcp-volunteer 落地后一次性替换成 Service 调用。
 * 模块依赖方向 {@code vcp-certification → vcp-system / vcp-volunteer} 本身是合法的。
 *
 * <p><b>累计时长的累加为什么用 UPDATE 而不是「查出来加一下再写回」</b>：
 * 读-改-写在并发审核下会丢更新（两个管理员同时通过同一学生的两条时长，后者覆盖前者），
 * 而这里是一条 {@code SET total_duration = COALESCE(total_duration, 0) + ?} 的原子语句。
 * 参数用 {@code #{}} 占位符绑定，不存在拼接注入。
 */
@Mapper
public interface DurationRefMapper {

    /**
     * 分页查询时长记录（前端列表用），一次 JOIN 拼齐前端要的全部字段。
     *
     * <p>筛选条件全部为空时不过滤；keyword 由调用方包成 {@code %关键字%} 后传入，
     * 同时匹配学生姓名与活动名称（与前端 mock 的行为一致）。
     *
     * <p>时间字段在 SQL 里用 to_char 直接格式化成前端约定的字符串
     * （{@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss}，与 DateTimeUtils 的输出一致）：
     * 本查询直接映射成 VO、不经实体中转，而前端是把时间字段直接渲染的，
     * 返回 LocalDateTime 会带上 ISO 的 T。
     *
     * <p>组织维度用 {@code COALESCE(sd.org_id, va.org_id)}：org_id 是后补的列，
     * 历史数据为空，回退到活动归属才能让组织端看到自己已提交的记录。
     * 同理活动类型用 {@code COALESCE(sd.activity_type, ac.category_name)} 兜底。
     *
     * @param page      分页对象，由 MyBatis-Plus 分页插件处理
     * @param status    状态精确筛选，可为空
     * @param college   学院精确筛选，可为空
     * @param orgId     组织筛选（组织管理员传本组织 id），可为空
     * @param studentId 学生筛选（学生传本人档案 id），可为空
     * @param keyword   姓名/活动名模糊匹配串，已包 %，可为空
     * @return 分页结果
     */
    @Select("<script>"
            + """
            SELECT sd.id,
                   sd.student_id,
                   si.student_no,
                   si.college,
                   su.real_name                                     AS student_name,
                   sd.activity_id,
                   va.title                                         AS activity_title,
                   COALESCE(sd.activity_type, ac.category_name)     AS activity_type,
                   COALESCE(sd.org_id, va.org_id)                   AS org_id,
                   oi.org_name,
                   sd.duration                                      AS hours,
                   to_char(va.start_time, 'YYYY-MM-DD')             AS service_date,
                   sd.status,
                   to_char(sd.submit_time, 'YYYY-MM-DD HH24:MI:SS') AS submitted_at,
                   to_char(sd.audit_time,  'YYYY-MM-DD HH24:MI:SS') AS audited_at,
                   au.real_name                                     AS auditor,
                   sd.audit_remark                                  AS remark,
                   sd.proof
              FROM service_duration sd
              LEFT JOIN student_info si       ON si.id = sd.student_id
              LEFT JOIN sys_user su           ON su.id = si.user_id
              LEFT JOIN volunteer_activity va ON va.id = sd.activity_id
              LEFT JOIN activity_category ac  ON ac.id = va.category_id
              LEFT JOIN org_info oi           ON oi.id = COALESCE(sd.org_id, va.org_id)
              LEFT JOIN sys_user au           ON au.id = sd.audit_user_id
             WHERE sd.deleted = 0
            <if test="status != null and status != ''">
               AND sd.status = #{status}
            </if>
            <if test="college != null and college != ''">
               AND si.college = #{college}
            </if>
            <if test="orgId != null">
               AND COALESCE(sd.org_id, va.org_id) = #{orgId}
            </if>
            <if test="studentId != null">
               AND sd.student_id = #{studentId}
            </if>
            <if test="keyword != null and keyword != ''">
               AND (su.real_name LIKE #{keyword} OR va.title LIKE #{keyword})
            </if>
             ORDER BY sd.id DESC
            </script>
            """)
    IPage<DurationVO> selectDurationPage(IPage<DurationVO> page,
                                        @Param("status") String status,
                                        @Param("college") String college,
                                        @Param("orgId") Long orgId,
                                        @Param("studentId") Long studentId,
                                        @Param("keyword") String keyword);

    /**
     * 查询单条时长记录，字段与列表一致。
     *
     * <p>SELECT 列表与 {@link #selectDurationPage} 必须保持一致，改一处要同时改另一处。
     *
     * @param id 时长记录 id
     * @return 记录；不存在或已逻辑删除时返回 null
     */
    @Select("""
            SELECT sd.id,
                   sd.student_id,
                   si.student_no,
                   si.college,
                   su.real_name                                     AS student_name,
                   sd.activity_id,
                   va.title                                         AS activity_title,
                   COALESCE(sd.activity_type, ac.category_name)     AS activity_type,
                   COALESCE(sd.org_id, va.org_id)                   AS org_id,
                   oi.org_name,
                   sd.duration                                      AS hours,
                   to_char(va.start_time, 'YYYY-MM-DD')             AS service_date,
                   sd.status,
                   to_char(sd.submit_time, 'YYYY-MM-DD HH24:MI:SS') AS submitted_at,
                   to_char(sd.audit_time,  'YYYY-MM-DD HH24:MI:SS') AS audited_at,
                   au.real_name                                     AS auditor,
                   sd.audit_remark                                  AS remark,
                   sd.proof
              FROM service_duration sd
              LEFT JOIN student_info si       ON si.id = sd.student_id
              LEFT JOIN sys_user su           ON su.id = si.user_id
              LEFT JOIN volunteer_activity va ON va.id = sd.activity_id
              LEFT JOIN activity_category ac  ON ac.id = va.category_id
              LEFT JOIN org_info oi           ON oi.id = COALESCE(sd.org_id, va.org_id)
              LEFT JOIN sys_user au           ON au.id = sd.audit_user_id
             WHERE sd.deleted = 0
               AND sd.id = #{id}
            """)
    DurationVO selectDurationDetail(@Param("id") Long id);

    /**
     * 按「活动 + 学生」反查报名 id。
     *
     * <p>service_duration.signup_id 是 NOT NULL UNIQUE，而前端提交时长时只给
     * studentId + activityId，必须由服务端反查，反查不到要报明确错误。
     *
     * <p><b>为什么必须显式过滤报名状态</b>：取消报名与审核驳回都只改 activity_signup.status、
     * 不做逻辑删除（见 vcp-volunteer 的 SignupServiceImpl.cancelSignup），所以 {@code deleted = 0}
     * 只说明这一行还在，不等于报名有效 —— 只看它会把已取消 / 已驳回 / 从未审核通过的报名
     * 一并放进来，而时长一旦审核通过就会累加 student_info.total_duration，且没有撤销入口。
     * 因此这里只认审核通过的报名：{@link SignupStatusEnum#APPROVED}（已通过）与
     * {@link SignupStatusEnum#COMPLETED}（已完成），SQL 里的两个字面量就是它们的码值。
     *
     * @param activityId 活动 id
     * @param studentId  学生档案 id
     * @return 报名 id；该学生在此活动下没有 APPROVED / COMPLETED 的报名时返回 null
     */
    @Select("""
            SELECT id
              FROM activity_signup
             WHERE activity_id = #{activityId}
               AND student_id = #{studentId}
               AND deleted = 0
               AND status IN ('APPROVED', 'COMPLETED')
             ORDER BY id DESC
             LIMIT 1
            """)
    Long selectSignupId(@Param("activityId") Long activityId, @Param("studentId") Long studentId);

    /**
     * 查活动的精简信息：提交时长时用于校验活动存在性、归属，并取权威的活动名称与分类名。
     *
     * @param id 活动 id
     * @return 活动精简信息；不存在时返回 null（活动为逻辑删除时也返回 null）
     */
    @Select("""
            SELECT va.id,
                   va.title,
                   va.org_id,
                   ac.category_name
              FROM volunteer_activity va
              LEFT JOIN activity_category ac ON ac.id = va.category_id
             WHERE va.id = #{id}
               AND va.deleted = 0
            """)
    ActivityBrief selectActivityBrief(@Param("id") Long id);

    /**
     * 按登录账号反查学生档案 id。
     *
     * <p>学生登录时会把 studentId 写进会话，正常路径读会话即可；本方法用于会话里
     * 取不到该键时的兜底（例如档案在登录之后才补录），避免「能登录却查不到自己的时长」。
     *
     * @param userId 账号 id（sys_user.id）
     * @return 学生档案 id；该账号没有在用档案时返回 null
     */
    @Select("""
            SELECT id
              FROM student_info
             WHERE user_id = #{userId}
               AND deleted = 0
             LIMIT 1
            """)
    Long selectStudentIdByUserId(@Param("userId") Long userId);

    /**
     * 查学生档案关联的账号 id，用于给时长审核结果发通知。
     *
     * <p>返回 null 表示学生档案不存在或已被逻辑删除 —— 调用方据此决定是报错（提交时）
     * 还是跳过通知并记 warn（审核时，此时记录已经存在，不该因为档案缺失就整笔回滚）。
     *
     * @param studentId 学生档案 id
     * @return 账号 id（sys_user.id）；档案不存在时返回 null
     */
    @Select("""
            SELECT user_id
              FROM student_info
             WHERE id = #{studentId}
               AND deleted = 0
            """)
    Long selectStudentUserId(@Param("studentId") Long studentId);

    /**
     * 把审核通过的时长累加到学生累计有效时长上。
     *
     * <p>student_info.total_duration 是累计时长的唯一事实来源（student_profile 只是画像快照），
     * 因此这里必须累加而不是覆盖；用一条原子 UPDATE 避免并发审核丢更新。
     * update_time 显式写入：本语句不走实体，MetaObjectHandler 不会介入。
     *
     * @param studentId 学生档案 id
     * @param hours     本次通过的时长（小时）
     * @return 影响行数；0 表示学生档案不存在或已逻辑删除
     */
    @Update("""
            UPDATE student_info
               SET total_duration = COALESCE(total_duration, 0) + #{hours},
                   update_time    = CURRENT_TIMESTAMP
             WHERE id = #{studentId}
               AND deleted = 0
            """)
    int addStudentTotalDuration(@Param("studentId") Long studentId, @Param("hours") BigDecimal hours);
}
