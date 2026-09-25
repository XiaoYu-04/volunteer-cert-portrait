package com.vcp.portrait.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.portrait.model.ActivitySpanRow;
import com.vcp.portrait.model.CategoryStatRow;
import com.vcp.portrait.model.PortraitRow;
import com.vcp.portrait.model.StudentStatRow;
import com.vcp.portrait.model.TagCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 画像的跨域只读聚合 Mapper。
 *
 * <p><b>为什么这里直接读别的域的表</b>：画像的本质就是跨域聚合 ——
 * 学生的累计时长在 {@code student_info}（vcp-system），已完成活动与分类在
 * {@code activity_signup} / {@code volunteer_activity} / {@code activity_category}
 * （vcp-volunteer），等级与标签的输入全在这三处，没有哪一家的 Service 接口能一次给齐
 * （按姓名/学院筛选、按分类分组、按学生批量取计数）。
 * 《公益等级与标签规则方案.md》§五 也是这么定的：「标签计算 → 聚合 activity_signup
 * + volunteer_activity + activity_category」。因此本类只做<b>只读投影</b>：
 * 一条 JOIN / 一条 GROUP BY 取数，绝不写别家的表（唯一例外见
 * {@link #updateWelfareLevel}），也不引用别家的实体与 Mapper。
 *
 * <p><b>唯一的写操作</b>是回写 {@code student_info.public_welfare_level} ——
 * 规则方案文档把「等级计算」明确划给 vcp-portrait（输入 total_duration、输出等级名写回该列），
 * 而 vcp-system 的 StudentService 目前只有只读方法，本轮又不改其他模块，故用一条
 * 单列 UPDATE 落地。它刻意只改一列 + update_time，不碰 total_duration（那是
 * vcp-certification 审核通过时的职责，见待办 A5）。
 *
 * <p><b>时长取谁</b>：两条画像查询里的 {@code total_duration} 都取
 * {@code student_info.total_duration}（权威值，见待办 A5 与《公益等级与标签规则方案》§五），
 * {@code student_profile.total_duration} 只作为画像快照由重算写入、供内容比对，读接口不返回它 ——
 * 否则「学生档案里是 14.4 小时、画像页显示 5.6 小时」这类两处口径打架会直接显示在页面上。
 *
 * <p><b>逻辑删除</b>：本类的查询都带 {@code deleted = 0}，而
 * {@code sql/04_demo_data.sql} 的画像生成语句没有带 —— 演示数据里没有任何逻辑删除行，
 * 两者结果完全一致；带上是更正确的口径（被软删的活动不该再计入画像）。
 *
 * <p><b>投影必须带一个恒非空列</b>：MyBatis 的 {@code returnInstanceForEmptyRow} 默认为
 * {@code false}（本项目没有改这个开关），一行里<b>所有映射列都为 NULL</b> 时，这一行不会映射成
 * 对象，而是变成 {@code List} 里的 {@code null} 元素 —— 遍历时直接 NPE，且不报任何错。
 * 因此本类每个返回投影的查询都至少带一个恒非空列（主键、外键或 {@code COUNT(*)}）。
 * 新增查询时请照此办理，原因与实测过程见 {@link TagCountRow}。
 */
@Mapper
public interface PortraitAggregateMapper {

    /**
     * 分页查询画像明细（按姓名/学号关键字、学院、标签筛选）。
     *
     * <p>三个筛选参数都传 null 时表示不筛选（空串由 Service 归一成 null）。
     * 标签筛选用「逗号包裹后 LIKE」的方式在逗号分隔串里做精确匹配，
     * 避免 {@code tags LIKE '%服务型%'} 把「社区服务型」和「校园服务型」之外的
     * 部分匹配也算进来。
     *
     * @param page    分页对象（由 MyBatis-Plus 分页插件下推 LIMIT/OFFSET）
     * @param keyword 关键字，匹配姓名或学号，可为 null
     * @param college 学院，精确匹配，可为 null
     * @param tag     画像标签，可为 null
     * @return 分页结果
     */
    @Select("""
            <script>
            SELECT sp.student_id           AS student_id,
                   u.real_name             AS student_name,
                   si.student_no           AS student_no,
                   si.college              AS college,
                   si.major                AS major,
                   si.grade                AS grade,
                   si.public_welfare_level AS level,
                   sp.total_activities     AS total_activities,
                   si.total_duration       AS total_duration,
                   sp.category_preference  AS category_preference,
                   sp.tags                 AS tags,
                   sp.update_time          AS generated_at
            FROM student_profile sp
            JOIN student_info si ON si.id = sp.student_id AND si.deleted = 0
            LEFT JOIN sys_user u ON u.id = si.user_id
            <where>
                <if test="keyword != null">
                    AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                         OR si.student_no LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="college != null">
                    AND si.college = #{college}
                </if>
                <if test="tag != null">
                    AND (',' || sp.tags || ',') LIKE CONCAT('%,', #{tag}, ',%')
                </if>
            </where>
            ORDER BY sp.student_id
            </script>
            """)
    Page<PortraitRow> selectPortraitPage(Page<PortraitRow> page,
                                         @Param("keyword") String keyword,
                                         @Param("college") String college,
                                         @Param("tag") String tag);

    /**
     * 取单个学生的画像明细。
     *
     * @param studentId 学生档案 id
     * @return 画像明细；画像尚未生成（student_profile 无行）时返回 null
     */
    @Select("""
            SELECT sp.student_id           AS student_id,
                   u.real_name             AS student_name,
                   si.student_no           AS student_no,
                   si.college              AS college,
                   si.major                AS major,
                   si.grade                AS grade,
                   si.public_welfare_level AS level,
                   sp.total_activities     AS total_activities,
                   si.total_duration       AS total_duration,
                   sp.category_preference  AS category_preference,
                   sp.tags                 AS tags,
                   sp.update_time          AS generated_at
            FROM student_profile sp
            JOIN student_info si ON si.id = sp.student_id AND si.deleted = 0
            LEFT JOIN sys_user u ON u.id = si.user_id
            WHERE sp.student_id = #{studentId}
            """)
    PortraitRow selectPortrait(@Param("studentId") Long studentId);

    /**
     * 按学生批量统计「各活动分类下已完成的活动次数」。
     *
     * <p>只统计 {@code COMPLETED} 的报名（真的签到参加了，而非仅报名通过），
     * 与 {@code sql/04_demo_data.sql} 的口径一致；按 {@code activity_id} 去重，
     * 一条报名对应一个活动，去重是为了将来出现重复行时计数不会翻倍。
     *
     * <p><b>studentIds 不能为空集合</b>：空集合会生成 {@code IN ()} 的非法 SQL。
     * 调用方（Service）负责先判空再调用；之所以不做成「空即查全部」，
     * 是因为那种「空条件静默变成全量」的写法正是 B19 点名的越权成因。
     *
     * @param studentIds 学生档案 id 集合，不可为空
     * @return 每个学生的每个分类一行；没有任何已完成活动的学生不会出现
     */
    @Select("""
            <script>
            SELECT sg.student_id                 AS student_id,
                   ac.id                         AS category_id,
                   ac.category_name              AS category_name,
                   ac.code                       AS category_code,
                   COUNT(DISTINCT sg.activity_id) AS activity_count
            FROM activity_signup sg
            JOIN volunteer_activity a ON a.id = sg.activity_id AND a.deleted = 0
            JOIN activity_category ac ON ac.id = a.category_id AND ac.deleted = 0
            WHERE sg.deleted = 0
              AND sg.status = 'COMPLETED'
              AND sg.student_id IN
            <foreach collection="studentIds" item="studentId" open="(" separator="," close=")">
                #{studentId}
            </foreach>
            GROUP BY sg.student_id, ac.id, ac.category_name, ac.code
            ORDER BY sg.student_id, ac.id
            </script>
            """)
    List<CategoryStatRow> selectCategoryStats(@Param("studentIds") Collection<Long> studentIds);

    /**
     * 按标签串分组统计人数，供画像标签分布接口使用。
     *
     * <p><b>为什么不在 Java 里数</b>：等价写法（把每行 tags 查回来再在 Service 里分组）需要
     * 实体投影，而 {@code tags} 可空，MyBatis 的 {@code returnInstanceForEmptyRow} 默认 false，
     * 「全部映射列都是 NULL」的行会以 null 元素进入 List，直接 NPE。这里由 SQL 分组，
     * 并且<b>投影里带了恒非空的 {@code COUNT(*)}</b>，null 元素从结构上不可能出现
     * （详见 {@link TagCountRow}）。
     *
     * <p>返回的是「标签组合 → 人数」（一个学生可带多个标签，所以一行可能代表若干个标签），
     * 拆分成单个标签后累加由 Service 负责；{@code tags IS NULL} 的学生（还没有任何标签）
     * 不产生分布项，故在 SQL 里直接排除。
     *
     * <p><b>必须 JOIN {@code student_info} 并过滤 {@code si.deleted = 0}</b>（2026-09-25 补，
     * 待办 B31 的同族缺口）：{@code student_profile} 没有 {@code deleted} 列，是整表重算的
     * 派生快照，账号被删除后快照行仍留在库里。此前本查询不 JOIN 档案表，被删账号会继续
     * 计入标签分布（实测删一个学生后「热心志愿者」从 1471 变成 1472，而学生总数已经减 1，
     * 两个数字互相打架且不报任何错）。同 Mapper 的
     * {@link #selectPortraitPage} / {@link #selectPortrait} / {@link #selectStudentStats}
     * 一直都是这么过滤的，本方法此前漏了，属于口径不一致。
     *
     * @return 每个不同的标签串一行，行数等于不同标签组合的个数（远小于学生数）
     */
    @Select("""
            SELECT sp.tags            AS tags,
                   COUNT(*)           AS profile_count
            FROM student_profile sp
            JOIN student_info si ON si.id = sp.student_id AND si.deleted = 0
            WHERE sp.tags IS NOT NULL
            GROUP BY sp.tags
            """)
    List<TagCountRow> selectTagCounts();

    /**
     * 取学生已完成活动的首末次时间，用于画像的「持续性」维度。
     *
     * <p><b>为什么多查一个 {@code COUNT(*)}</b>：只有 MIN/MAX 两列时，学生没有已完成活动
     * （两列都为 NULL）会让整行映射结果全为 null，MyBatis 会把该行变成 {@code null} 而不是
     * 一个空对象（{@code returnInstanceForEmptyRow} 默认 false）。加上恒非空的计数列，
     * 本方法就不会返回 null，调用方拿到的是「次数 0 + 两个 null 时间」这种可读的语义。
     * Service 侧仍按 null 安全处理（次数为 0 时「持续性」记 0 分）。
     *
     * @param studentId 学生档案 id
     * @return 首末次时间与已完成活动次数；该学生没有已完成活动时次数为 0、两个时间为 null
     */
    @Select("""
            SELECT MIN(COALESCE(a.start_time, sg.signup_time)) AS first_time,
                   MAX(COALESCE(a.start_time, sg.signup_time)) AS last_time,
                   COUNT(*)                                    AS completed_count
            FROM activity_signup sg
            JOIN volunteer_activity a ON a.id = sg.activity_id AND a.deleted = 0
            WHERE sg.deleted = 0
              AND sg.status = 'COMPLETED'
              AND sg.student_id = #{studentId}
            """)
    ActivitySpanRow selectActivitySpan(@Param("studentId") Long studentId);

    /**
     * 取学生的累计有效时长与现有公益等级（画像重算的输入）。
     *
     * <p>时长只读 {@code student_info.total_duration} —— 它是权威值（待办 A5），
     * 由 vcp-certification 在时长审核通过时维护，画像模块不重算它，
     * 避免两个模块各算一遍、口径还不一致。
     *
     * @param studentId 指定学生档案 id；传 null 表示取全部学生（重算全量场景）
     * @return 学生统计行，按 id 升序
     */
    @Select("""
            <script>
            SELECT si.id                     AS student_id,
                   si.total_duration         AS total_duration,
                   si.public_welfare_level   AS public_welfare_level
            FROM student_info si
            WHERE si.deleted = 0
            <if test="studentId != null">
                AND si.id = #{studentId}
            </if>
            ORDER BY si.id
            </script>
            """)
    List<StudentStatRow> selectStudentStats(@Param("studentId") Long studentId);

    /**
     * 回写公益等级（本 Mapper 唯一的写操作，理由见类注释）。
     *
     * <p>显式写 {@code update_time}：PostgreSQL 没有 {@code ON UPDATE CURRENT_TIMESTAMP}，
     * 本库也没有 {@code BEFORE UPDATE} 触发器，而自定义 UPDATE 又不经过
     * {@code VcpMetaObjectHandler}（它只处理实体对象的 insert/update）——
     * 不写这一列，「最后修改时间」会永远停在建行时间（待办 B17）。
     *
     * @param studentId  学生档案 id
     * @param level      等级中文名，取自 {@code PublicWelfareLevelEnum.getLevelName()}
     * @param updateTime 更新时间，由调用方传入以便整批重算共用同一个时间点
     * @return 影响行数
     */
    @Update("""
            UPDATE student_info
            SET public_welfare_level = #{level},
                update_time          = #{updateTime}
            WHERE id = #{studentId}
            """)
    int updateWelfareLevel(@Param("studentId") Long studentId,
                           @Param("level") String level,
                           @Param("updateTime") LocalDateTime updateTime);
}
