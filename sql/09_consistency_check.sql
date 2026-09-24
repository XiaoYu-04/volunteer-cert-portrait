-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 9：一致性自检（20 项，只读，可重复执行）
-- 数据库类型：PostgreSQL 16+（本项目实测 PostgreSQL 18.6）
-- =============================================================
--
-- 【用途】
--   一条 SQL 输出「20 项一致性检查 + 1 行汇总」的结果集，供 D3 测试报告、
--   验收与答辩当场演示：违规数全 0 即通过。
--   把 2026-09-22 实跑时临时在客户端里敲的核对语句固化成脚本，不必再靠记忆重敲。
--
-- 【只读保证】
--   · 全文只有 SELECT / WITH，没有 INSERT / UPDATE / DELETE / ALTER / CREATE / DROP；
--   · 不建临时表、不 BEGIN、不 SET、不写任何数据 —— 跑完之后数据库与跑之前完全一致；
--   · 不用 psql 元命令（无 \d / \echo / \set），psql 与 JDBC 单文件程序都能整段执行；
--   · 结果只由库里的数据决定（无 random()；now() 仅作第 16 项「审核时间是否落在未来」
--     的比较基准），同一份数据在任何机器上跑都得到同一张表。
--
-- 【执行方式】
--   psql -U postgres -d volunteer_cert_portrait -f sql/09_consistency_check.sql
--   或在 Navicat / DBeaver / JDBC 里整段执行，读唯一的结果集。
--
-- 【前置条件】
--   01 → 02 → 03 → [04] → 05 → 06 必须已执行（本脚本用到 05 补的 org_info.college）。
--   07 是**可选**的：没跑过 07 的库同样不会报错，只是部分检查项（如第 15 项的学院取值
--   集合、第 20 项的标签覆盖）会命中非 0 —— 那是演示数据规模不足的正常现象，
--   不是脚本错误，也不会中断执行。
--
-- 【20 项检查的分组】（编号是全项目统一口径，不得增删条目、不得改编号）
--   外键悬空             ① ② ③ ④ ⑤ ⑥
--   汇总字段与明细不符   ⑦ ⑧ ⑨ ⑩
--   状态与审核字段矛盾   ⑪ ⑫ ⑬ ⑭
--   学院专业错配         ⑮
--   时间异常             ⑯ ⑰
--   演示数据完整性       ⑱ ⑲ ⑳
--
-- 【口径来源与诚实说明】
--   本脚本把 2026-09-22 实跑时临时核对的 20 项一致性自检固化下来，条数与当时口径一致；当时未留存逐条清单，本表按文档记录的五类问题重建
--   （文档按五类归档，本脚本细分为 6 个 category：「学院专业错配」单独成类，条目数不变）。
--   列名一律以 sql/02_schema.sql 为准，并逐条核对了 05 / 06 / 07 的补列语句；
--   凡口头清单用词与 schema 实际列名不一致的，按 schema 实现并在该项注释里注明。
--   计数口径（与 04 / 07 的聚合回填、运行期实现保持一致）：
--     · 有 deleted 列的表只统计 deleted = 0 的业务行；无 deleted 列的表（关联表、
--       流水/日志表、画像快照表）不加这个条件；
--     · 外键悬空检查是唯一例外 —— 外键是物理约束，父行是否逻辑删除不影响
--       「这条引用指向不存在的行」这一事实，故不加 deleted 过滤；
--     · signed_count = 报名即占名额 = activity_signup.status IN
--       ('PENDING','APPROVED','COMPLETED') 且 deleted = 0；
--     · 时长权威值是 student_info.total_duration，student_profile 只是画像快照；
--     · 公益等级 6 档（演示尺度）：<3 普通 / 3-6 一星 / 6-10 二星 / 10-20 三星 /
--       20-40 四星 / >=40 五星，与 04 第八节、07 第 11.2 节、PublicWelfareLevelEnum 一致；
--     · 取消报名是 status='CANCELED' 复用同一行，deleted 保持 0。
--
-- 【结果判读】
--   违规数全 0 即通过（汇总行 violations = 0）。
--   汇总行：check_no = 99、category = '汇总'、check_name 形如「检查项总数 20，违规合计 0」。
--   本脚本只报数、不改数；命中非 0 时请人工确认后再决定是否修数据。
-- =============================================================

WITH checks AS (

    -- ---------------------------------------------------------
    -- 一、外键悬空（6 项）
    --
    -- 这 6 条在正常库上恒为 0（02 已建物理外键），价值在于：
    --   · 论文/答辩时用同一张表证明「引用完整性没有问题」；
    --   · 若有人手工删过数据或丢过约束，这 6 条会立刻报出来。
    -- 注意：本组**不加** deleted 过滤，理由见文件头「计数口径」。
    -- ---------------------------------------------------------

    -- ① 报名 → 活动
    SELECT 1 AS check_no, '外键悬空' AS category,
           'activity_signup.activity_id 悬空（无对应 volunteer_activity）' AS check_name,
           (SELECT COUNT(*)
              FROM activity_signup sg
             WHERE NOT EXISTS (SELECT 1 FROM volunteer_activity va WHERE va.id = sg.activity_id)) AS violations

    UNION ALL

    -- ② 报名 → 学生档案（注意 student_id 是 student_info.id，不是 sys_user.id）
    SELECT 2, '外键悬空',
           'activity_signup.student_id 悬空（无对应 student_info）',
           (SELECT COUNT(*)
              FROM activity_signup sg
             WHERE NOT EXISTS (SELECT 1 FROM student_info si WHERE si.id = sg.student_id))

    UNION ALL

    -- ③ 签到记录 → 报名
    SELECT 3, '外键悬空',
           'attendance_record.signup_id 悬空（无对应 activity_signup）',
           (SELECT COUNT(*)
              FROM attendance_record ar
             WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg WHERE sg.id = ar.signup_id))

    UNION ALL

    -- ④ 服务时长 → 报名
    SELECT 4, '外键悬空',
           'service_duration.signup_id 悬空（无对应 activity_signup）',
           (SELECT COUNT(*)
              FROM service_duration sd
             WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg WHERE sg.id = sd.signup_id))

    UNION ALL

    -- ⑤ 公益画像 → 学生档案
    SELECT 5, '外键悬空',
           'student_profile.student_id 悬空（无对应 student_info）',
           (SELECT COUNT(*)
              FROM student_profile sp
             WHERE NOT EXISTS (SELECT 1 FROM student_info si WHERE si.id = sp.student_id))

    UNION ALL

    -- ⑥ 活动 → 组织（org_id 可空，NULL 不算悬空：DRAFT 阶段尚未指定组织）
    SELECT 6, '外键悬空',
           'volunteer_activity.org_id 悬空（无对应 org_info）',
           (SELECT COUNT(*)
              FROM volunteer_activity va
             WHERE va.org_id IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM org_info o WHERE o.id = va.org_id))

    -- ---------------------------------------------------------
    -- 二、汇总字段与明细不符（4 项）
    -- ---------------------------------------------------------

    UNION ALL

    -- ⑦ 活动的已报名人数与明细不符
    --   口径：signed_count = 报名即占名额 = status IN ('PENDING','APPROVED','COMPLETED')
    --   且 deleted = 0（取消/驳回各释放一个名额）。04 第八节与 07 第 11.4 节的刷新
    --   语句没有加 sg.deleted 过滤，但演示库里报名无逻辑删除行，两者等价。
    --   用 IS DISTINCT FROM 而非 <>：<> 遇到 NULL 会得到 NULL（不计数），
    --   那样 signed_count IS NULL 反而会被判为「合规」。
    SELECT 7, '汇总字段与明细不符',
           'volunteer_activity.signed_count ≠ 报名明细计数（报名即占名额口径）',
           (SELECT COUNT(*)
              FROM volunteer_activity va
             WHERE va.deleted = 0
               AND va.signed_count IS DISTINCT FROM (
                       SELECT COUNT(*)
                         FROM activity_signup sg
                        WHERE sg.activity_id = va.id
                          AND sg.deleted = 0
                          AND sg.status IN ('PENDING', 'APPROVED', 'COMPLETED')))

    UNION ALL

    -- ⑧ 学生累计时长与已通过明细不符（权威值口径：APPROVED 的时长合计）
    SELECT 8, '汇总字段与明细不符',
           'student_info.total_duration ≠ service_duration 中 APPROVED 时长合计',
           (SELECT COUNT(*)
              FROM student_info si
             WHERE si.deleted = 0
               AND COALESCE(si.total_duration, 0) IS DISTINCT FROM COALESCE((
                       SELECT SUM(sd.duration)
                         FROM service_duration sd
                        WHERE sd.student_id = si.id
                          AND sd.deleted = 0
                          AND sd.status = 'APPROVED'), 0))

    UNION ALL

    -- ⑨ 画像快照的时长与权威值不符
    --   student_profile 无 deleted 列（画像表是整表重算的快照，见 07 第 11.5 节
    --   的 DELETE + INSERT），故本项只对学生侧的 deleted 做过滤。
    SELECT 9, '汇总字段与明细不符',
           'student_profile.total_duration ≠ student_info.total_duration（画像快照过期）',
           (SELECT COUNT(*)
              FROM student_profile sp
              JOIN student_info si ON si.id = sp.student_id
             WHERE si.deleted = 0
               AND COALESCE(sp.total_duration, 0) IS DISTINCT FROM COALESCE(si.total_duration, 0))

    UNION ALL

    -- ⑩ 画像快照的参与次数与 COMPLETED 报名数不符
    --   「COMPLETED 状态的报名数」按 04 第九节的口径实现为
    --   COUNT(DISTINCT sg.activity_id)：uk_activity_student(activity_id, student_id)
    --   保证同一活动同一学生只有一行，故与行数等价。
    SELECT 10, '汇总字段与明细不符',
           'student_profile.total_activities ≠ COMPLETED 状态的报名数',
           (SELECT COUNT(*)
              FROM student_profile sp
              JOIN student_info si ON si.id = sp.student_id
             WHERE si.deleted = 0
               AND COALESCE(sp.total_activities, 0) IS DISTINCT FROM (
                       SELECT COUNT(DISTINCT sg.activity_id)
                         FROM activity_signup sg
                        WHERE sg.student_id = sp.student_id
                          AND sg.deleted = 0
                          AND sg.status = 'COMPLETED'))

    -- ---------------------------------------------------------
    -- 三、状态与审核字段矛盾（4 项）
    --
    -- 实际列名（以 02_schema.sql 为准，口头清单只说了「审核人/审核时间」）：
    --   service_duration.audit_user_id / service_duration.audit_time
    --   activity_signup.audit_user_id  / activity_signup.audit_time
    -- 注意：duration_audit（审核流水表）**没有 audit_time 列**，它只有 create_time
    --   （只追加流水，见 02 第 15 张表的说明），所以本组的「审核时间」一律取上面两张表。
    -- ---------------------------------------------------------

    UNION ALL

    -- ⑪ 已通过的时长记录却没有审核人/审核时间
    SELECT 11, '状态与审核字段矛盾',
           'service_duration.status = APPROVED 却缺 audit_user_id 或 audit_time',
           (SELECT COUNT(*)
              FROM service_duration sd
             WHERE sd.deleted = 0
               AND sd.status = 'APPROVED'
               AND (sd.audit_user_id IS NULL OR sd.audit_time IS NULL))

    UNION ALL

    -- ⑫ 待审核的时长记录却已经有审核时间
    --   本项按清单原文只判 audit_time；audit_user_id 是否同时为空不在此项范围
    --   （它与 ⑪ 的「缺审核人」互补，若要一并判会改动口径，需先过团队评审）。
    SELECT 12, '状态与审核字段矛盾',
           'service_duration.status = PENDING_AUDIT 却已有 audit_time',
           (SELECT COUNT(*)
              FROM service_duration sd
             WHERE sd.deleted = 0
               AND sd.status = 'PENDING_AUDIT'
               AND sd.audit_time IS NOT NULL)

    UNION ALL

    -- ⑬ 已通过的报名却没有审核时间（报名审核时间列名为 activity_signup.audit_time）
    SELECT 13, '状态与审核字段矛盾',
           'activity_signup.status = APPROVED 却缺 audit_time',
           (SELECT COUNT(*)
              FROM activity_signup sg
             WHERE sg.deleted = 0
               AND sg.status = 'APPROVED'
               AND sg.audit_time IS NULL)

    UNION ALL

    -- ⑭ 已签到的签到记录却没有签退时间
    --   签到签退的实际列名：attendance_record.sign_in_time / sign_out_time；
    --   「已签到」= status = 'SIGNED_IN'（枚举：NOT_SIGNED / SIGNED_IN / SIGNED_OUT /
    --   ABNORMAL / ABSENT）。ABNORMAL 是有签退但时间异常的记录，不属本项。
    SELECT 14, '状态与审核字段矛盾',
           'attendance_record.status = SIGNED_IN 却缺 sign_out_time',
           (SELECT COUNT(*)
              FROM attendance_record ar
             WHERE ar.deleted = 0
               AND ar.status = 'SIGNED_IN'
               AND ar.sign_out_time IS NULL)

    -- ---------------------------------------------------------
    -- 四、学院专业错配（1 项）
    -- ---------------------------------------------------------

    UNION ALL

    -- ⑮ 学生的学院为空，或不在组织挂靠学院的取值集合内
    --   实际列名：student_info.college 与 org_info.college（后者由 05 补列、07 第 2.1 节
    --   回填）。子查询里剔掉 NULL/空串，避免 NOT IN 遇 NULL 返回 NULL 而漏判。
    --   ⚠️ 未执行 07 的库上 org_info.college 全为 NULL，本项会全量命中（等于学生数），
    --      这是预期现象：此时学院筛选与组织筛选确实对不上，不是脚本错误。
    SELECT 15, '学院专业错配',
           'student_info.college 为空或不在 org_info.college 取值集合内',
           (SELECT COUNT(*)
              FROM student_info si
             WHERE si.deleted = 0
               AND (si.college IS NULL
                    OR btrim(si.college) = ''
                    OR si.college NOT IN (
                           SELECT o.college
                             FROM org_info o
                            WHERE o.deleted = 0
                              AND o.college IS NOT NULL
                              AND btrim(o.college) <> '')))

    -- ---------------------------------------------------------
    -- 五、时间异常（2 项）
    -- ---------------------------------------------------------

    UNION ALL

    -- ⑯ 审核时间落在未来（> now()）
    --   全库只有两张表带审核时间：activity_signup.audit_time、service_duration.audit_time
    --   （org_info 只有 audit_remark，没有审核时间列；duration_audit 只有 create_time）。
    --   04 用 LEAST(..., CURRENT_TIMESTAMP) 写审核时间，就是为了让本项恒为 0。
    SELECT 16, '时间异常',
           '审核时间落在未来（activity_signup / service_duration 的 audit_time > now()）',
           ((SELECT COUNT(*) FROM activity_signup sg
              WHERE sg.deleted = 0 AND sg.audit_time > now())
            + (SELECT COUNT(*) FROM service_duration sd
                WHERE sd.deleted = 0 AND sd.audit_time > now()))

    UNION ALL

    -- ⑰ 时间倒挂：签退早于签到，或活动结束早于开始
    SELECT 17, '时间异常',
           '时间倒挂（sign_out_time < sign_in_time 或 end_time < start_time）',
           ((SELECT COUNT(*) FROM attendance_record ar
              WHERE ar.deleted = 0
                AND ar.sign_in_time IS NOT NULL
                AND ar.sign_out_time IS NOT NULL
                AND ar.sign_out_time < ar.sign_in_time)
            + (SELECT COUNT(*) FROM volunteer_activity va
                WHERE va.deleted = 0
                  AND va.start_time IS NOT NULL
                  AND va.end_time IS NOT NULL
                  AND va.end_time < va.start_time))

    -- ---------------------------------------------------------
    -- 六、演示数据完整性（3 项）
    -- ---------------------------------------------------------

    UNION ALL

    -- ⑱ 已报名超名额
    --   max_count = 0 表示「不限人数」（见 02 该列注释），不参与比较 ——
    --   否则不限名额的活动会被误判为超额。
    SELECT 18, '演示数据完整性',
           'volunteer_activity.signed_count > max_count（已报名超名额，max_count=0 不限除外）',
           (SELECT COUNT(*)
              FROM volunteer_activity va
             WHERE va.deleted = 0
               AND va.max_count > 0
               AND va.signed_count > va.max_count)

    UNION ALL

    -- ⑲ 公益等级与累计时长按 6 档阈值不匹配
    --   阈值（演示尺度，团队已确认）：<3 普通 / 3-6 一星 / 6-10 二星 / 10-20 三星 /
    --   20-40 四星 / >=40 五星。等级取值为中文串「普通志愿者 / 一星志愿者 / …
    --   / 五星志愿者」，与 04 第八节、07 第 11.2 节一致。
    --   边界说明：CASE 自上而下短路且用 >= 判定，故 3.0 判一星、6.0 判二星、10.0 判三星、
    --   20.0 判四星、40.0 判五星；total_duration 为 NULL 按 0 处理（判「普通志愿者」）。
    SELECT 19, '演示数据完整性',
           'student_info.public_welfare_level 与 total_duration 按 6 档阈值不匹配',
           (SELECT COUNT(*)
              FROM student_info si
             WHERE si.deleted = 0
               AND COALESCE(si.public_welfare_level, '') IS DISTINCT FROM
                   CASE
                       WHEN COALESCE(si.total_duration, 0) >= 40 THEN '五星志愿者'
                       WHEN COALESCE(si.total_duration, 0) >= 20 THEN '四星志愿者'
                       WHEN COALESCE(si.total_duration, 0) >= 10 THEN '三星志愿者'
                       WHEN COALESCE(si.total_duration, 0) >= 6  THEN '二星志愿者'
                       WHEN COALESCE(si.total_duration, 0) >= 3  THEN '一星志愿者'
                       ELSE '普通志愿者'
                   END)

    UNION ALL

    -- ⑳ 画像标签覆盖不足 8 类（缺档）
    --   8 类标签取自 04 第九节 / 07 第 11.5 节的标签规则（tags 为逗号分隔串）：
    --     热心志愿者、长期坚持型、校园服务型、社区服务型、环保行动型、大型活动型、
    --     助老服务型、文化传播型。
    --   口径：本项是**全库覆盖度**检查 —— 违规数 = 8 类里「一个学生都没拿到」的类数，
    --   而不是某个学生的标签不完整（单人标签规则下，一个学生本来就拿不到 8 类）。
    --   按 8 类逐类判「是否有人拿到」，btrim 容忍逗号后带空格的写法。
    SELECT 20, '演示数据完整性',
           'student_profile.tags 覆盖不足 8 类标签（缺档数）',
           (SELECT COUNT(*)
              FROM (VALUES ('热心志愿者'), ('长期坚持型'), ('校园服务型'), ('社区服务型'),
                           ('环保行动型'), ('大型活动型'), ('助老服务型'), ('文化传播型')) AS t(tag)
             WHERE NOT EXISTS (
                       SELECT 1
                         FROM student_profile sp
                        WHERE sp.tags IS NOT NULL
                          AND btrim(sp.tags) <> ''
                          AND EXISTS (SELECT 1
                                        FROM unnest(string_to_array(sp.tags, ',')) AS u(tag)
                                       WHERE btrim(u.tag) = t.tag)))
)

-- -------------------------------------------------------------
-- 输出：20 行检查结果 + 1 行汇总（check_no = 99），按 check_no 升序
--   violations 为 0 的检查项表示通过；汇总行 violations 为全部违规合计。
-- -------------------------------------------------------------
SELECT check_no, category, check_name, violations
  FROM checks

UNION ALL

SELECT 99,
       '汇总',
       '检查项总数 ' || (SELECT COUNT(*) FROM checks)::text
                     || '，违规合计 ' || (SELECT COALESCE(SUM(violations), 0) FROM checks)::bigint::text,
       (SELECT COALESCE(SUM(violations), 0) FROM checks)::bigint

ORDER BY check_no;
