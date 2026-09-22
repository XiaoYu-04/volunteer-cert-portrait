-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 4/4：演示数据（可选，仅供开发调试与答辩演示）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 规模（依据《开发计划与分工.md》第九阶段"测试数据"要求）：
--   组织 8 个（其中 2 个留作待审核，便于演示组织审核流程）
--   活动 20 个（12 个已结束 / 6 个已发布 / 1 个草稿 / 1 个已取消）
--   学生 30 个 + 基础脚本里的 1 个 = 31 个
--   报名 149 条、其中已完成 81 条（精确值：由活动 id 与取模共同决定，非随机波动）
--   另生成对应签到记录 81 条、服务时长记录 72 条、审核流水 135 条、通知 212 条
--   （以上均为在 PostgreSQL 18.6 上实跑得到的实测值）
--
-- 前置条件：必须先执行 03_init_data.sql（本脚本依赖其中的角色、账号、分类）
--
-- 数据生成方式：全部使用确定性算法（取模/取余）而非 random()，
--   因此每次执行生成的数据完全一致，便于团队三人看到相同的演示效果。
--   如需重新生成，先执行 02_schema.sql 清表再依次执行 03、04。
--
-- ⚠️ 本脚本仅供开发/演示环境使用，切勿在生产环境执行。
-- =============================================================

SET client_encoding = 'UTF8';

-- =============================================================
-- 一、追加 7 个组织及其管理员账号
--     组织 id 2..8，对应管理员账号 id 4..10（org_admin2..org_admin8）
-- =============================================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, status)
SELECT g + 2,
       'org_admin' || g,
       '123456',
       (ARRAY['李明','王芳','张伟','刘洋','陈静','杨磊','赵敏'])[g - 1],
       '139' || lpad(g::text, 8, '0'),
       'org' || g || '@example.com',
       1
FROM generate_series(2, 8) AS g;

INSERT INTO sys_user_role (user_id, role_id)
SELECT g + 2, 2 FROM generate_series(2, 8) AS g;

INSERT INTO org_info (id, contact_user_id, org_name, org_type, contact_name, phone, email, description, status)
SELECT g,
       g + 2,
       (ARRAY['阳光社区志愿服务队','绿芽环保公益社','校园文化传播协会','青春助老服务队',
              '大学生赛事志愿服务团','红十字急救志愿队','乡村支教服务团'])[g - 1],
       (ARRAY['社区组织','社会团体','学院组织','社会团体','学院组织','社会团体','社会团体'])[g - 1],
       (ARRAY['李明','王芳','张伟','刘洋','陈静','杨磊','赵敏'])[g - 1],
       '139' || lpad(g::text, 8, '0'),
       'org' || g || '@example.com',
       '经学校批准成立的志愿服务组织，长期开展各类公益活动。',
       -- 组织 7、8 留为待审核，用于演示学校管理员的组织资质审核流程
       CASE WHEN g <= 6 THEN 'APPROVED' ELSE 'PENDING' END
FROM generate_series(2, 8) AS g;

-- =============================================================
-- 二、追加 30 个学生账号（id 11..40）与学生档案（id 2..31）
-- =============================================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, status)
SELECT g + 10,
       'stu' || lpad(g::text, 4, '0'),
       '123456',
       (ARRAY['张','王','李','赵','刘','陈','杨','黄','周','吴'])[1 + (g % 10)]
           || (ARRAY['伟','芳','娜','敏','静','强','磊','洋','艳','勇'])[1 + (g % 10)],
       '137' || lpad(g::text, 8, '0'),
       'stu' || lpad(g::text, 4, '0') || '@example.com',
       1
FROM generate_series(1, 30) AS g;

INSERT INTO sys_user_role (user_id, role_id)
SELECT g + 10, 1 FROM generate_series(1, 30) AS g;

INSERT INTO student_info (id, user_id, student_no, college, major, class_name, total_duration, public_welfare_level)
SELECT g + 1,
       g + 10,
       -- 学号从 20230002 起，避开 03_init_data.sql 里基础学生已占用的 20230001
       '2023' || lpad((g + 1)::text, 4, '0'),
       -- 学院 / 专业 / 班级 必须用同一个下标。若各用不同模数（如 5 与 6），
       -- 会出现"经济管理学院-电子信息工程技术"这类现实中不存在的组合，30 个学生里会有 24 个错配
       (ARRAY['计算机学院','电子信息学院','经济管理学院','外国语学院','机械工程学院','计算机学院'])[1 + (g % 6)],
       (ARRAY['软件技术','电子信息工程技术','工商企业管理','商务英语','机械设计与制造','计算机应用技术'])[1 + (g % 6)],
       (ARRAY['软件2301','电信2301','工商2301','商英2301','机制2301','计应2302'])[1 + (g % 6)],
       0,
       '普通志愿者'
FROM generate_series(1, 30) AS g;

-- =============================================================
-- 三、20 个志愿活动
--     1-12 已结束（CLOSED）→ 用于生成报名/签到/时长与看板数据
--     13-18 已发布（PUBLISHED）→ 用于演示报名流程
--     19 草稿、20 已取消 → 用于演示状态筛选
--     仅 APPROVED 状态的组织（id 1..6）可发布活动
-- =============================================================
INSERT INTO volunteer_activity
    (id, title, category_id, org_id, start_time, end_time, location, max_count, duration, status, description) VALUES
-- ---- 已结束 ----
(1,  '校园图书馆整理志愿服务', 1, 1, CURRENT_TIMESTAMP - INTERVAL '58 days', CURRENT_TIMESTAMP - INTERVAL '58 days' + INTERVAL '3 hours', '学校图书馆',      30, 3.0, 'CLOSED', '协助图书馆整理书籍、维护阅读秩序。'),
(2,  '阳光社区环境清洁',       2, 2, CURRENT_TIMESTAMP - INTERVAL '52 days', CURRENT_TIMESTAMP - INTERVAL '52 days' + INTERVAL '4 hours', '阳光社区',        25, 4.0, 'CLOSED', '清理社区公共区域垃圾，美化居住环境。'),
(3,  '校园绿化带养护',         3, 3, CURRENT_TIMESTAMP - INTERVAL '49 days', CURRENT_TIMESTAMP - INTERVAL '49 days' + INTERVAL '2 hours', '学校南区绿化带',  20, 2.0, 'CLOSED', '修剪绿植、清理杂草，维护校园绿化。'),
(4,  '敬老院陪伴服务',         5, 5, CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '45 days' + INTERVAL '4 hours', '幸福敬老院',      18, 4.0, 'CLOSED', '陪伴老人聊天、协助开展文娱活动。'),
(5,  '校运会志愿服务',         4, 6, CURRENT_TIMESTAMP - INTERVAL '42 days', CURRENT_TIMESTAMP - INTERVAL '42 days' + INTERVAL '6 hours', '学校田径场',      50, 6.0, 'CLOSED', '协助校运会赛事组织、检录与秩序维护。'),
(6,  '校园文化节志愿讲解',     6, 4, CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '38 days' + INTERVAL '3 hours', '学校大学生活动中心', 22, 3.0, 'CLOSED', '为校园文化节参观者提供讲解与引导。'),
(7,  '图书馆新生导览',         1, 1, CURRENT_TIMESTAMP - INTERVAL '34 days', CURRENT_TIMESTAMP - INTERVAL '34 days' + INTERVAL '2 hours', '学校图书馆',      28, 2.0, 'CLOSED', '带领新生熟悉图书馆布局与借阅流程。'),
(8,  '社区便民维修服务',       2, 2, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days' + INTERVAL '5 hours', '阳光社区服务中心', 16, 5.0, 'CLOSED', '为社区居民提供小家电检修与维护服务。'),
(9,  '河道垃圾清理行动',       3, 3, CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '27 days' + INTERVAL '3 hours', '校园河道沿线',    35, 3.0, 'CLOSED', '清理河道两侧垃圾，宣传环保理念。'),
(10, '老年手机课堂',           5, 5, CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days' + INTERVAL '2 hours', '阳光社区活动室',  15, 2.0, 'CLOSED', '教老年人使用智能手机的常用功能。'),
(11, '马拉松赛事保障',         4, 6, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days' + INTERVAL '8 hours', '城市滨江跑道',    60, 8.0, 'CLOSED', '为马拉松赛事提供补给与引导服务。'),
(12, '非遗文化宣传周',         6, 4, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days' + INTERVAL '4 hours', '学校文化广场',    24, 4.0, 'CLOSED', '宣传非物质文化遗产，协助展台布置与讲解。'),
-- ---- 已发布（未来）----
(13, '校园迎新志愿服务',       1, 1, CURRENT_TIMESTAMP + INTERVAL '2 days',  CURRENT_TIMESTAMP + INTERVAL '2 days'  + INTERVAL '6 hours', '学校南门广场',    40, 6.0, 'PUBLISHED', '迎接新生报到，提供咨询与行李搬运服务。'),
(14, '社区助老送温暖',         5, 2, CURRENT_TIMESTAMP + INTERVAL '5 days',  CURRENT_TIMESTAMP + INTERVAL '5 days'  + INTERVAL '3 hours', '阳光社区',        20, 3.0, 'PUBLISHED', '走访社区独居老人，送去生活物资。'),
(15, '世界环境日宣传',         3, 3, CURRENT_TIMESTAMP + INTERVAL '9 days',  CURRENT_TIMESTAMP + INTERVAL '9 days'  + INTERVAL '3 hours', '学校中心广场',    45, 3.0, 'PUBLISHED', '开展环保知识宣传与垃圾分类引导。'),
(16, '图书馆周末志愿服务',     1, 1, CURRENT_TIMESTAMP + INTERVAL '12 days', CURRENT_TIMESTAMP + INTERVAL '12 days' + INTERVAL '3 hours', '学校图书馆',      26, 3.0, 'PUBLISHED', '周末协助图书馆整理书架与借还书。'),
(17, '高校篮球联赛志愿服务',   4, 6, CURRENT_TIMESTAMP + INTERVAL '16 days', CURRENT_TIMESTAMP + INTERVAL '16 days' + INTERVAL '5 hours', '学校体育馆',      32, 5.0, 'PUBLISHED', '协助高校篮球联赛的赛事保障工作。'),
(18, '社区暑期课堂',           2, 2, CURRENT_TIMESTAMP + INTERVAL '21 days', CURRENT_TIMESTAMP + INTERVAL '21 days' + INTERVAL '4 hours', '阳光社区活动室',  18, 4.0, 'PUBLISHED', '为社区儿童提供暑期课业辅导与兴趣课堂。'),
-- ---- 其他状态 ----
(19, '校园摄影展志愿服务',     6, 4, CURRENT_TIMESTAMP + INTERVAL '28 days', CURRENT_TIMESTAMP + INTERVAL '28 days' + INTERVAL '3 hours', '学校美术馆',      20, 3.0, 'DRAFT',     '协助校园摄影展布展与现场引导（筹备中）。'),
(20, '校园清洁日（因故取消）', 3, 3, CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP + INTERVAL '30 days' + INTERVAL '2 hours', '学校北区',        30, 2.0, 'CANCELED',  '原定校园清洁日活动，因场地调整取消。');

-- =============================================================
-- 四、报名记录
--     每个活动抽取 5~12 名学生（用取模做确定性打散，保证各活动选到不同的人）
--     已结束的活动 → 绝大多数 COMPLETED；已发布的活动 → 多为 APPROVED / PENDING
-- =============================================================
INSERT INTO activity_signup (activity_id, student_id, signup_time, status, audit_user_id, audit_time, audit_remark)
SELECT a.id,
       s.id,
       a.start_time - (INTERVAL '1 day' * (2 + (s.id % 5))),
       CASE
           WHEN a.status = 'CLOSED' THEN
               CASE WHEN (s.id * 3 + a.id * 7) % 20 < 17 THEN 'COMPLETED'
                    WHEN (s.id * 3 + a.id * 7) % 20 < 19 THEN 'REJECTED'
                    ELSE 'CANCELED' END
           ELSE
               CASE WHEN (s.id * 3 + a.id * 7) % 20 < 12 THEN 'APPROVED'
                    WHEN (s.id * 3 + a.id * 7) % 20 < 19 THEN 'PENDING'
                    ELSE 'REJECTED' END
       END,
       -- 审核人：只有"经组织管理员审核过"的报名才有审核人
       --   已结束活动：COMPLETED(余数 0..16) 与 REJECTED(17..18) 审核过；
       --               CANCELED(19) 是学生自行取消，无审核人
       --   已发布活动：APPROVED(0..11) 与 REJECTED(19) 审核过；
       --               PENDING(12..18) 待审核，无审核人
       CASE WHEN a.status = 'CLOSED'     AND (s.id * 3 + a.id * 7) % 20 <= 18 THEN o.contact_user_id
            WHEN a.status = 'PUBLISHED' AND ((s.id * 3 + a.id * 7) % 20 <= 11
                                          OR (s.id * 3 + a.id * 7) % 20 = 19) THEN o.contact_user_id
            ELSE NULL END,
       -- 用 LEAST 兜底：已发布活动的 start_time 在未来，直接减 12 小时会算出"未来的审核时间"
       CASE WHEN a.status = 'CLOSED'     AND (s.id * 3 + a.id * 7) % 20 <= 18
                 THEN LEAST(a.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP)
            WHEN a.status = 'PUBLISHED' AND ((s.id * 3 + a.id * 7) % 20 <= 11
                                          OR (s.id * 3 + a.id * 7) % 20 = 19)
                 THEN LEAST(a.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP)
            ELSE NULL END,
       -- 审核意见：仅驳回时填写（取消不是审核动作，不填）
       CASE WHEN a.status = 'CLOSED'     AND (s.id * 3 + a.id * 7) % 20 IN (17, 18) THEN '该同学已有同类活动记录，名额有限'
            WHEN a.status = 'PUBLISHED' AND (s.id * 3 + a.id * 7) % 20 = 19 THEN '报名信息不完整，请补充联系方式'
            ELSE NULL END
FROM volunteer_activity a
JOIN org_info o ON o.id = a.org_id
CROSS JOIN LATERAL (
    SELECT si2.id
    FROM student_info si2
    ORDER BY (si2.id * 13 + a.id * 29) % 37
    LIMIT 5 + (a.id % 8)
) s
WHERE a.status IN ('CLOSED', 'PUBLISHED');

-- =============================================================
-- 五、签到签退记录
--     仅对已完成的报名生成：80% 正常签退，10% 异常，10% 缺勤
-- =============================================================
INSERT INTO attendance_record (signup_id, sign_in_time, sign_out_time, status, remark)
SELECT sg.id,
       CASE WHEN (sg.id * 7) % 10 < 9 THEN a.start_time + INTERVAL '10 minutes' ELSE NULL END,
       -- 异常记录的时间要真的偏离正常值，否则备注说"提前离场"、数据却与正常记录完全相同，自相矛盾
       CASE WHEN (sg.id * 7) % 10 < 8 THEN a.end_time
            WHEN (sg.id * 7) % 10 < 9 THEN a.start_time + INTERVAL '50 minutes'
            ELSE NULL END,
       CASE WHEN (sg.id * 7) % 10 < 8 THEN 'SIGNED_OUT'
            WHEN (sg.id * 7) % 10 < 9 THEN 'ABNORMAL'
            ELSE 'ABSENT' END,
       CASE WHEN (sg.id * 7) % 10 < 8 THEN '正常签到签退'
            WHEN (sg.id * 7) % 10 < 9 THEN '提前离场，签退时间明显早于活动结束时间，已由组织管理员标记'
            ELSE '未到场，记为缺勤' END
FROM activity_signup sg
JOIN volunteer_activity a ON a.id = sg.activity_id
WHERE sg.status = 'COMPLETED';

-- =============================================================
-- 六、服务时长记录
--     仅对已签到（含异常）的报名生成；缺勤不产生时长
--     75% 审核通过，15% 待审核，10% 驳回
-- =============================================================
INSERT INTO service_duration (signup_id, activity_id, student_id, duration, status,
                              submit_time, audit_user_id, audit_time, audit_remark)
SELECT sg.id,
       sg.activity_id,
       sg.student_id,
       -- 实际时长 = 签退时间 - 签到时间，保留 1 位小数（与 NUMERIC(10,1) 对齐）
       -- ::numeric 强转：PG14 之前 EXTRACT 返回 double precision，而 PG 没有 round(double, int) 重载
       ROUND((EXTRACT(EPOCH FROM (ar.sign_out_time - ar.sign_in_time)) / 3600.0)::numeric, 1),
       CASE WHEN (sg.id * 11) % 20 < 15 THEN 'APPROVED'
            WHEN (sg.id * 11) % 20 < 18 THEN 'PENDING_AUDIT'
            ELSE 'REJECTED' END,
       a.end_time + INTERVAL '1 day',
       -- 只有"已通过"(余数 <15) 或"已驳回"(余数 >=18) 才有审核人与审核时间；
       -- "待审核"(余数 15..17) 不应有审核人 —— 条件写反会导致待审核的反而带审核人
       CASE WHEN (sg.id * 11) % 20 < 15 OR (sg.id * 11) % 20 >= 18
            THEN (SELECT id FROM sys_user WHERE username = 'admin') ELSE NULL END,
       CASE WHEN (sg.id * 11) % 20 < 15 OR (sg.id * 11) % 20 >= 18
            THEN a.end_time + INTERVAL '2 days' ELSE NULL END,
       CASE WHEN (sg.id * 11) % 20 >= 18 THEN '服务时长与签到记录不符，请重新核对后提交' ELSE NULL END
FROM activity_signup sg
JOIN attendance_record ar ON ar.signup_id = sg.id
JOIN volunteer_activity a ON a.id = sg.activity_id
WHERE sg.status = 'COMPLETED'
  AND ar.status IN ('SIGNED_OUT', 'ABNORMAL');

-- =============================================================
-- 七、时长审核流水（每次提交 + 每次审核各留一条）
-- =============================================================
INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id, o.contact_user_id, 'SUBMIT', '活动已结束，提交本活动服务时长'
FROM service_duration sd
JOIN volunteer_activity a ON a.id = sd.activity_id
JOIN org_info o ON o.id = a.org_id;

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id,
       (SELECT id FROM sys_user WHERE username = 'admin'),
       CASE WHEN sd.status = 'APPROVED' THEN 'APPROVE' ELSE 'REJECT' END,
       sd.audit_remark
FROM service_duration sd
WHERE sd.status IN ('APPROVED', 'REJECTED');

-- =============================================================
-- 八、汇总字段回填
--     这两步必须在报名/时长数据生成之后执行，否则汇总值是错的
-- =============================================================

-- 学生累计有效时长：只统计审核通过的时长
UPDATE student_info si
SET total_duration = COALESCE((
        SELECT SUM(sd.duration)
        FROM service_duration sd
        WHERE sd.student_id = si.id
          AND sd.status = 'APPROVED'
    ), 0);

-- 活动已报名人数：审核通过 + 已完成 占用名额（与"是否报满"判断一致）
UPDATE volunteer_activity va
SET signed_count = (
        SELECT COUNT(*)
        FROM activity_signup sg
        WHERE sg.activity_id = va.id
          AND sg.status IN ('APPROVED', 'COMPLETED')
    );

-- =============================================================
-- 九、学生公益画像
--
-- ⚠️⚠️ 重要说明：标签（tags）与等级（public_welfare_level）的判定规则
--      在项目文档（开发计划 / 知识库 / 选题对话）中【均未定义】。
--      下面的标签规则是【我为了生成可展示的演示数据而临时拟定的占位规则】，
--      不是文档结论，正式实现时必须由团队自行拍板后重写。
--      占位规则：
--        · 累计时长 > 0                    → 「热心志愿者」
--        · 参与活动次数 >= 3               → 「长期坚持型」
--          （阈值取 3 而非 5：演示数据里单个学生最多只有 4 次已完成活动，取 5 会导致该标签永远不出现）
--        · 按参与最多的活动分类追加：校园服务型 / 社区服务型 / 环保行动型 / 大型活动型
--          （助老服务、文化传播两类在文档给出的 6 个标签名里没有对应项，不追加）
-- =============================================================
INSERT INTO student_profile (student_id, total_activities, total_duration,
                             category_preference, tags, portrait_desc)
SELECT si.id,
       (SELECT COUNT(DISTINCT sg.activity_id)
        FROM activity_signup sg
        WHERE sg.student_id = si.id AND sg.status = 'COMPLETED'),
       si.total_duration,
       -- 偏好活动类型：参与次数最多的分类
       (SELECT ac.category_name
        FROM activity_signup sg
        JOIN volunteer_activity a ON a.id = sg.activity_id
        JOIN activity_category ac ON ac.id = a.category_id
        WHERE sg.student_id = si.id AND sg.status = 'COMPLETED'
        GROUP BY ac.category_name
        -- 用 id 而非中文名打破并列：中文串的排序结果依赖数据库 collation，
        -- 换成 MIN(ac.id) 才能保证任何机器上执行结果都一致
        ORDER BY COUNT(*) DESC, MIN(ac.id)
        LIMIT 1),
       -- 公益标签（占位规则，见上方说明）
       -- 外层套 NULLIF：三个 CASE 全为 NULL 时 CONCAT_WS 返回空字符串 ''（不是 NULL），
       -- 前端 tags.split(',') 会渲染出一个空标签
       NULLIF(CONCAT_WS(',',
           CASE WHEN si.total_duration > 0 THEN '热心志愿者' END,
           -- 阈值取 3 而非 5：演示数据里单个学生最多只有 4 次已完成活动，取 5 该标签永远不会出现
           CASE WHEN (SELECT COUNT(DISTINCT sg.activity_id)
                      FROM activity_signup sg
                      WHERE sg.student_id = si.id AND sg.status = 'COMPLETED') >= 3
                THEN '长期坚持型' END,
           CASE (SELECT ac.category_name
                 FROM activity_signup sg
                 JOIN volunteer_activity a ON a.id = sg.activity_id
                 JOIN activity_category ac ON ac.id = a.category_id
                 WHERE sg.student_id = si.id AND sg.status = 'COMPLETED'
                 GROUP BY ac.category_name
                 ORDER BY COUNT(*) DESC, MIN(ac.id)
                 LIMIT 1)
               WHEN '校园服务' THEN '校园服务型'
               WHEN '社区服务' THEN '社区服务型'
               WHEN '环保公益' THEN '环保行动型'
               WHEN '大型赛事' THEN '大型活动型'
           END), ''),
       '该同学累计参与志愿活动 ' ||
       (SELECT COUNT(DISTINCT sg.activity_id)
        FROM activity_signup sg
        WHERE sg.student_id = si.id AND sg.status = 'COMPLETED') ||
       ' 次，累计有效志愿时长 ' || si.total_duration || ' 小时。'
FROM student_info si;

-- =============================================================
-- 十、通知（报名结果 + 时长审核结果 + 一条系统公告）
-- =============================================================
INSERT INTO notification (user_id, title, content, type, is_read)
SELECT si.user_id,
       CASE WHEN sg.status = 'COMPLETED' THEN '活动已完成' ELSE '报名审核通过' END,
       CASE WHEN sg.status = 'COMPLETED'
            THEN '您报名的活动「' || a.title || '」已完成，服务时长待组织管理员提交。'
            ELSE '您报名的活动「' || a.title || '」已通过审核。' END,
       'SIGNUP',
       -- 约 1/3 标记为已读，便于演示未读角标
       (sg.id % 3 = 0)
FROM activity_signup sg
JOIN student_info si ON si.id = sg.student_id
JOIN volunteer_activity a ON a.id = sg.activity_id
WHERE sg.status IN ('APPROVED', 'COMPLETED');

INSERT INTO notification (user_id, title, content, type, is_read)
SELECT si.user_id,
       CASE WHEN sd.status = 'APPROVED' THEN '服务时长审核通过' ELSE '服务时长审核驳回' END,
       '您参与的「' || a.title || '」服务时长 ' || sd.duration || ' 小时' ||
       CASE WHEN sd.status = 'APPROVED' THEN '已通过审核，已计入累计时长。'
            ELSE '被驳回：' || COALESCE(sd.audit_remark, '请核对后重新提交。') END,
       'DURATION',
       (sd.id % 2 = 0)
FROM service_duration sd
JOIN student_info si ON si.id = sd.student_id
JOIN volunteer_activity a ON a.id = sd.activity_id
WHERE sd.status IN ('APPROVED', 'REJECTED');

INSERT INTO notification (user_id, title, content, type, is_read)
SELECT u.id,
       '系统公告',
       '志愿服务时长认证与公益画像系统已完成初始化，欢迎使用。',
       'SYSTEM',
       FALSE
FROM sys_user u;

-- =============================================================
-- 十一、修复自增序列（本脚本对部分表显式指定了 id）
-- =============================================================
SELECT setval(pg_get_serial_sequence('sys_user',           'id'), COALESCE((SELECT MAX(id) FROM sys_user),           1));
SELECT setval(pg_get_serial_sequence('student_info',       'id'), COALESCE((SELECT MAX(id) FROM student_info),       1));
SELECT setval(pg_get_serial_sequence('org_info',           'id'), COALESCE((SELECT MAX(id) FROM org_info),           1));
SELECT setval(pg_get_serial_sequence('volunteer_activity', 'id'), COALESCE((SELECT MAX(id) FROM volunteer_activity), 1));

-- =============================================================
-- 验证
-- =============================================================
-- SELECT 'org'        AS t, COUNT(*) FROM org_info
-- UNION ALL SELECT 'activity',  COUNT(*) FROM volunteer_activity
-- UNION ALL SELECT 'student',   COUNT(*) FROM student_info
-- UNION ALL SELECT 'signup',    COUNT(*) FROM activity_signup
-- UNION ALL SELECT 'attendance',COUNT(*) FROM attendance_record
-- UNION ALL SELECT 'duration',  COUNT(*) FROM service_duration
-- UNION ALL SELECT 'audit',     COUNT(*) FROM duration_audit
-- UNION ALL SELECT 'profile',   COUNT(*) FROM student_profile
-- UNION ALL SELECT 'notify',    COUNT(*) FROM notification;
--
-- 看板数据自检：各学院累计有效志愿时长排名
-- SELECT si.college, ROUND(SUM(sd.duration), 1) AS total_hours, COUNT(DISTINCT si.id) AS students
-- FROM service_duration sd
-- JOIN student_info si ON si.id = sd.student_id
-- WHERE sd.status = 'APPROVED'
-- GROUP BY si.college
-- ORDER BY total_hours DESC;