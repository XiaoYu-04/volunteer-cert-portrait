-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 6/6：后端落地所需的补列与补索引（第二批）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 【为什么需要这个脚本】
--   05_backend_gap_fix.sql 之后，前端 28 个页面写死的字段契约仍有几处对不上
--   （docs/待办清单.md 的 B16），且 B17 点名的索引缺口还有一批没补：
--     · 活动：报名截止 deadline、联系方式 contact 两列不存在
--     · 报名：报名理由 reason 不存在，而前端 createSignup 会提交它
--     · 分类：activity_category 没有 code 列，而分类管理页有「分类编码」列
--     · 学生档案：性别 gender、年级 grade 不存在（姓名/手机号仍在 sys_user，不冗余）
--     · 时长：服务证明 proof、所属组织 org_id、活动类型 activity_type 不存在
--     · 索引：attendance_record 除 UNIQUE(signup_id) 外没有任何索引，而 analytics
--       的 10 个接口都要按签到时间聚合它；另有 6 处外键列没有索引（见第七节）
--
-- 【本脚本只做加法】
--   全部是 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / 幂等的唯一约束、
--   以及**只填 NULL 行**的确定性回填，不改列类型、不删列、不覆盖任何已有值，
--   因此可以安全地重复执行。
--
-- 【执行顺序】
--   01 → 02 → 03 → [04] → 05 → 本脚本。
--   必须晚于 05：第七节的索引清单是按「05 执行后」的状态核对出来的（05 已建
--   notification(user_id, is_read)，本脚本只再声明一次做幂等兜底，不会重复建）。
--
-- 【⚠️ 本脚本没有在真实数据库上实跑过】
--   编写环境没有 psql、也连不上远端库，因此这是**静态核对**的产物：
--   表名/列名/约束名逐条对照 02_schema.sql 与 05_backend_gap_fix.sql 确认过，
--   但没有实跑。请在库上执行后运行第九节的自检 SQL 逐条确认。
--   另注意：本脚本与 04_demo_data.sql 一样是多语句隐式事务（见 CLAUDE.md 第 8 条），
--   任何一条报错都会**整批回滚**，不会"只少建几个索引"。
-- =============================================================

SET client_encoding = 'UTF8';

-- -------------------------------------------------------------
-- 一、volunteer_activity 补 deadline / contact（B16）
-- -------------------------------------------------------------
-- 活动列表/详情页与报名页都要显示这两列：deadline 用于「报名截止」展示与
-- 「报名已截止」判断，contact 是发布组织留下的联系方式。
ALTER TABLE volunteer_activity ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;
ALTER TABLE volunteer_activity ADD COLUMN IF NOT EXISTS contact  VARCHAR(100);

COMMENT ON COLUMN volunteer_activity.deadline IS '报名截止时间；前端字段 deadline，活动列表与报名页展示，过期不可报名';
COMMENT ON COLUMN volunteer_activity.contact  IS '活动联系方式；前端字段 contact，如「李同学 138****2201」，由发布组织填写';

-- -------------------------------------------------------------
-- 二、activity_signup 补 reason（B16）
-- -------------------------------------------------------------
-- 前端 createSignup 会提交 reason（报名理由），组织管理员审核报名时要在列表里看到它。
ALTER TABLE activity_signup ADD COLUMN IF NOT EXISTS reason VARCHAR(500);

COMMENT ON COLUMN activity_signup.reason IS '报名理由；学生报名时提交（前端字段 reason），组织管理员审核报名时参考';

-- -------------------------------------------------------------
-- 三、activity_category 补 code + remark + 唯一约束（B16）
-- -------------------------------------------------------------
-- 分类管理页有一列「分类编码」，新增/编辑表单里也是可填项（占位符「如 COMMUNITY」，
-- 留空时前端 mock 会生成 CUSTOM_n）。原表没有该列，接口无处存取。
ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS code VARCHAR(50);

COMMENT ON COLUMN activity_category.code IS '分类编码：英文大写，用于接口与统计口径（前端字段 code）；唯一，新建分类留空时由后端生成 CUSTOM_n';

-- remark：分类备注，分类管理页可填。实体 ActivityCategory 映射了该列，
-- 缺列时新建/修改分类接口会直接 500。
-- 【2026-09-27 从已删除的 07_demo_scale.sql 迁移过来】：原补列语句写在 07 里，
-- 07 删除后新环境重建库会缺这一列，故并入本脚本（补列脚本才是它该待的地方）。
ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS remark VARCHAR(255);

COMMENT ON COLUMN activity_category.remark IS '分类备注；分类管理页可填，前端字段 remark';

-- 回填已有的 6 条分类（03_init_data.sql 里的 校园服务/社区服务/环保公益/大型赛事/助老服务/文化传播）。
--   ⚠️ 必须**按分类名**映射，不能按 id 或下标映射：
--   前端 dataset.js 的 categories 是用 types.map((t, i) => ({ id: i + 1, ... })) 生成的，
--   它的 id 顺序与库里的 6 条分类**并不一致**（库里 id 1 是「校园服务」，前端 id 1 是「社区服务」）。
--   按下标映射会把编码整体错位，且不会报任何错。
--   前端 6 个编码（dataset.js:127）：
--     COMMUNITY 社区服务 / ENVIRONMENT 环保行动 / TEACHING 支教助学 /
--     EVENT 大型赛会 / CAMPUS 校园服务 / ELDERLY 敬老助残。
--   按名称语义对齐后，5 条能直接对上：
--     校园服务→CAMPUS  社区服务→COMMUNITY  环保公益→ENVIRONMENT
--     大型赛事→EVENT   助老服务→ELDERLY
--   剩下的「文化传播」在前端 mock 里没有对应项（mock 那一项是「支教助学」/TEACHING），
--   故按语义取 CULTURE。若团队决定把库里的分类改名对齐前端（或把 code 改成 TEACHING），
--   见第十节第 4 条的现成语句。
--   只填 code IS NULL 的行：管理员若已通过接口改过编码，重复执行不会覆盖它。
UPDATE activity_category SET code = v.code
  FROM (VALUES
        ('校园服务', 'CAMPUS'),
        ('社区服务', 'COMMUNITY'),
        ('环保公益', 'ENVIRONMENT'),
        ('大型赛事', 'EVENT'),
        ('助老服务', 'ELDERLY'),
        ('文化传播', 'CULTURE')
       ) AS v(category_name, code)
 WHERE activity_category.category_name = v.category_name
   AND activity_category.code IS NULL;

-- 唯一约束。PostgreSQL 没有 ADD CONSTRAINT IF NOT EXISTS，故用 DO 块先查 pg_constraint
-- （顺带查 pg_class，避免同名的索引已存在时 ADD CONSTRAINT 报重名），保证可重复执行。
-- 用约束而不是 CREATE UNIQUE INDEX，是为了与 02_schema.sql 里 uk_activity_student /
-- uk_dict_type_key 的风格一致；约束背后同样是一个唯一索引，查询性能无差别。
-- 注意：分类是逻辑删除（有 deleted 列），软删除的行仍占着 code。若要允许复用已删除分类的
-- 编码，需改成部分唯一索引 UNIQUE (code) WHERE deleted = 0 —— 与 sql/README.md 待定项 7
-- 是同一个取舍，本脚本按 B16 的要求建全量唯一约束。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'uk_category_code'
                      AND conrelid = 'activity_category'::regclass)
       AND NOT EXISTS (SELECT 1 FROM pg_class
                    WHERE relname = 'uk_category_code'
                      AND relkind = 'i') THEN
        ALTER TABLE activity_category ADD CONSTRAINT uk_category_code UNIQUE (code);
    END IF;
END $$;

-- -------------------------------------------------------------
-- 四、student_info 补 gender / grade（B16）
-- -------------------------------------------------------------
-- 学生列表与档案页要显示性别、年级；前端 mock 的取值是 MALE/FEMALE 与 '2022'/'2023'/'2024'。
-- 姓名与手机号**不**冗余到学生表：它们分别在 sys_user.real_name / sys_user.phone，
-- 由接口层 JOIN 或拼装（B16 已明确这一条）。
ALTER TABLE student_info ADD COLUMN IF NOT EXISTS gender VARCHAR(10);
ALTER TABLE student_info ADD COLUMN IF NOT EXISTS grade  VARCHAR(20);

COMMENT ON COLUMN student_info.gender IS '性别：MALE男 / FEMALE女（英文码，与前端 student 数据一致）';
COMMENT ON COLUMN student_info.grade  IS '年级：入学年份，如 2022 / 2023（前端学生列表与档案页展示）';

-- -------------------------------------------------------------
-- 五、service_duration 补 proof / org_id / activity_type（B16）
-- -------------------------------------------------------------
-- 时长列表（学生端「我的时长」、学校端「时长审核」）要显示服务证明与所属组织/活动类型，
-- 前端 mock 的 durations 每行都带 proof / orgId / orgName / activityType。
--   · proof          —— 服务证明（照片或材料的文件名/URL）
--   · org_id         —— 所属组织，可由 activity_id → volunteer_activity.org_id 反查，
--                       但看板要按组织汇总时长，存成冗余列可省一次 JOIN
--                       （与已有的 activity_id / student_id 两个冗余列同一取舍）
--   · activity_type  —— 活动分类名（前端字段 activityType）。前端给的就是**分类名**而非
--                       category_id，故按名存快照，分类改名不影响历史记录
--                       （B18 已指出「分类→标签」以中文名为键很脆弱，此处同源，需在 B10
--                        落地时统一事实来源）
ALTER TABLE service_duration ADD COLUMN IF NOT EXISTS proof         VARCHAR(255);
ALTER TABLE service_duration ADD COLUMN IF NOT EXISTS org_id        BIGINT;
ALTER TABLE service_duration ADD COLUMN IF NOT EXISTS activity_type VARCHAR(50);

COMMENT ON COLUMN service_duration.proof         IS '服务证明附件（文件名或 URL）；前端字段 proof，学生提交时长时上传，审核时查看';
COMMENT ON COLUMN service_duration.org_id        IS '所属组织 → org_info.id（冗余，便于看板按组织汇总时长）；未加物理外键约束，理由见第七节';
COMMENT ON COLUMN service_duration.activity_type IS '活动分类名快照（前端字段 activityType）；冗余，便于按分类统计而不必再 JOIN volunteer_activity + activity_category';

-- -------------------------------------------------------------
-- 六、补索引：签到聚合（B17）
-- -------------------------------------------------------------
-- attendance_record 原先只有 UNIQUE(signup_id) 一个索引，而 analytics 的 10 个接口
-- 全都要在这张表上聚合：
--   · 按签到日期/时间范围出趋势、活跃时段分布 → sign_in_time
--   · 先按签到状态过滤（签到率 / 缺勤率 / 异常签到）再按时间聚合 → (status, sign_in_time)
-- 不单独建 status 单列索引：复合索引的最左前缀已经能服务「仅按 status 过滤」的查询，
-- 与 02_schema.sql 里 idx_signup_student_status 顺带覆盖「仅按 student_id」是同一做法。
-- 若后续 analytics 的 SQL 定型为纯时间范围聚合（不带 status），走 idx_attendance_sign_in_time；
-- 两者配合即可覆盖这 10 个接口，暂时不必再加别的。
CREATE INDEX IF NOT EXISTS idx_attendance_sign_in_time      ON attendance_record (sign_in_time);
CREATE INDEX IF NOT EXISTS idx_attendance_status_sign_in_time ON attendance_record (status, sign_in_time);

-- -------------------------------------------------------------
-- 七、补索引：外键列缺口（B17）
-- -------------------------------------------------------------
-- PostgreSQL **不会**为外键列自动建索引（与 MySQL 不同）。
-- 逐条核对 02_schema.sql 里全部 18 个外键约束后，**仍缺索引的是下面 6 处**：
--   sys_user_role.role_id           ← uk_user_role(user_id, role_id) 的最左前缀是 user_id，不含 role_id
--   volunteer_activity.category_id  ← 只有 (org_id, status)，category_id 没索引
--   activity_signup.audit_user_id   ← (activity_id, student_id) 与 (student_id, status) 都不含它
--   service_duration.audit_user_id  ← 同上
--   duration_audit.duration_id      ← 已有 128 条流水（04 实测值），审核页要按它取全部流水
--   duration_audit.auditor_id
-- 已覆盖、**不要重复建**的（核对结论，写在这里免得后来人再补一遍）：
--   student_info.user_id（NOT NULL UNIQUE）、attendance_record.signup_id、
--   service_duration.signup_id、student_profile.student_id（均被各自的 UNIQUE 约束覆盖）；
--   volunteer_activity.org_id（idx_activity_org_status 最左前缀）；
--   activity_signup.activity_id（uk_activity_student 最左前缀）；
--   activity_signup.student_id（idx_signup_student_status 最左前缀）；
--   service_duration.activity_id（idx_duration_activity）；
--   service_duration.student_id（idx_duration_student_status 最左前缀）；
--   notification.user_id（idx_notification_user）；
--   org_info.contact_user_id 由 05 的部分唯一索引 uk_org_info_contact_user 覆盖
--   （该索引不含 deleted = 1 的行，删用户时的外键检查仍会全表扫；org_info 只有 8 行，
--    不值得再建一个全量索引，故这里不补）。
CREATE INDEX IF NOT EXISTS idx_user_role_role          ON sys_user_role (role_id);
CREATE INDEX IF NOT EXISTS idx_activity_category       ON volunteer_activity (category_id);
CREATE INDEX IF NOT EXISTS idx_signup_audit_user       ON activity_signup (audit_user_id);
CREATE INDEX IF NOT EXISTS idx_duration_audit_user     ON service_duration (audit_user_id);
CREATE INDEX IF NOT EXISTS idx_duration_audit_duration ON duration_audit (duration_id);
CREATE INDEX IF NOT EXISTS idx_duration_audit_auditor  ON duration_audit (auditor_id);

-- 第五节新增的 service_duration.org_id 是事实上的外键（指向 org_info.id），同样需要索引。
-- 但**不加物理外键约束**：已有行的 org_id 由第八节回填产生，若库里存在活动已被删除等
-- 历史脏数据，加约束会让整个脚本回滚；列注释里已写明它指向 org_info.id。
CREATE INDEX IF NOT EXISTS idx_duration_org            ON service_duration (org_id);

-- B17 点名的另外三个聚合索引：
--   notification(user_id, is_read) —— 05 已建同名索引 idx_notification_user_read。
--     这里再声明一次只是幂等兜底（IF NOT EXISTS 不会重复建）：只跑过 02+03 的库
--     执行本脚本后也会有它，不必回头再跑 05。
CREATE INDEX IF NOT EXISTS idx_notification_user_read  ON notification (user_id, is_read);
--   activity_signup(signup_time)：学生端「我的报名」按报名时间倒序、看板按天统计报名数
CREATE INDEX IF NOT EXISTS idx_signup_time             ON activity_signup (signup_time);
--   service_duration(create_time)：时长列表按创建时间排序/按天统计（前端 submittedAt 列）
CREATE INDEX IF NOT EXISTS idx_duration_create_time    ON service_duration (create_time);

-- -------------------------------------------------------------
-- 八、回填已有数据（全部确定性派生，不含 random()）
-- -------------------------------------------------------------
-- 规则：只写 NULL 的行，重复执行不会改动任何已有值；所有派生都只依赖库中已有数据，
-- 因此三位同学执行后结果完全一致（与 04_demo_data.sql 的约定相同）。

-- 1) 活动的报名截止时间：取开始时间前 2 天。
--    仅为让 04 的 20 条演示活动在页面上有「报名截止」可显示；
--    真实活动的截止时间由组织管理员发布时填写。
UPDATE volunteer_activity
   SET deadline = start_time - INTERVAL '2 days'
 WHERE deadline IS NULL
   AND start_time IS NOT NULL;

-- 2) 活动联系方式：取发布组织的负责人与电话，拼成「李明 13900000002」，
--    与前端 mock 的 contact 格式（「李同学 138****2201」）同形。
UPDATE volunteer_activity a
   SET contact = o.contact_name || ' ' || o.phone
  FROM org_info o
 WHERE o.id = a.org_id
   AND a.contact IS NULL
   AND o.contact_name IS NOT NULL
   AND o.phone IS NOT NULL;

-- 3) 时长记录的所属组织与活动分类名：由 activity_id 反查得到，不是编造。
UPDATE service_duration sd
   SET org_id = a.org_id
  FROM volunteer_activity a
 WHERE a.id = sd.activity_id
   AND sd.org_id IS NULL;

UPDATE service_duration sd
   SET activity_type = c.category_name
  FROM volunteer_activity a
  JOIN activity_category c ON c.id = a.category_id
 WHERE a.id = sd.activity_id
   AND sd.activity_type IS NULL;

-- 4) 学生性别与年级：库里没有来源，按与前端 mock 相同的约定派生
--    （dataset.js 是 gender: i % 2 === 0 ? 'MALE' : 'FEMALE'、grade: ['2022','2023','2024'][i % 3]，
--      i 从 0 开始；学生 id = i + 1，故这里用 id - 1 对齐同一套规则，
--      让演示数据与前端 mock 的观感一致）。⚠️ 仅供演示数据补空，
--      真实学生档案应由学生本人或管理员在页面上维护。
UPDATE student_info
   SET gender = CASE WHEN (id - 1) % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END
 WHERE gender IS NULL;

UPDATE student_info
   SET grade = (ARRAY['2022', '2023', '2024'])[1 + ((id - 1) % 3)]
 WHERE grade IS NULL;

-- 有两列**故意不回填**，免得被当成漏做：
--   · activity_signup.reason —— 报名理由是学生本人写的，库里没有来源，
--     编造出来没有意义。04 的 149 条演示报名保持 NULL（前端对空值渲染为「—」），
--     新报名会带上。
--   · service_duration.proof —— 同理，证明附件要么真有文件要么就空着；
--     统一填一个假文件名会让审核页显示成「已上传证明」，属于误导。

-- -------------------------------------------------------------
-- 九、自检：确认列、约束、索引都已就位
-- -------------------------------------------------------------

-- 9.1 列（期望：9 行全部 ok = 1）
SELECT 'volunteer_activity.deadline' AS item, COUNT(*) AS ok FROM information_schema.columns WHERE table_name = 'volunteer_activity' AND column_name = 'deadline'
UNION ALL
SELECT 'volunteer_activity.contact',        COUNT(*) FROM information_schema.columns WHERE table_name = 'volunteer_activity' AND column_name = 'contact'
UNION ALL
SELECT 'activity_signup.reason',            COUNT(*) FROM information_schema.columns WHERE table_name = 'activity_signup'    AND column_name = 'reason'
UNION ALL
SELECT 'activity_category.code',            COUNT(*) FROM information_schema.columns WHERE table_name = 'activity_category'  AND column_name = 'code'
UNION ALL
SELECT 'student_info.gender',               COUNT(*) FROM information_schema.columns WHERE table_name = 'student_info'       AND column_name = 'gender'
UNION ALL
SELECT 'student_info.grade',                COUNT(*) FROM information_schema.columns WHERE table_name = 'student_info'       AND column_name = 'grade'
UNION ALL
SELECT 'service_duration.proof',            COUNT(*) FROM information_schema.columns WHERE table_name = 'service_duration'   AND column_name = 'proof'
UNION ALL
SELECT 'service_duration.org_id',           COUNT(*) FROM information_schema.columns WHERE table_name = 'service_duration'   AND column_name = 'org_id'
UNION ALL
SELECT 'service_duration.activity_type',    COUNT(*) FROM information_schema.columns WHERE table_name = 'service_duration'   AND column_name = 'activity_type';

-- 9.2 唯一约束（期望：ok = 1）
SELECT 'uk_category_code 唯一约束' AS item, COUNT(*) AS ok
  FROM pg_constraint
 WHERE conname = 'uk_category_code'
   AND conrelid = 'activity_category'::regclass;

-- 9.3 索引（期望：12 行全部 ok = 1）
SELECT 'idx_attendance_sign_in_time' AS item, COUNT(*) AS ok FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_attendance_sign_in_time'
UNION ALL
SELECT 'idx_attendance_status_sign_in_time', COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_attendance_status_sign_in_time'
UNION ALL
SELECT 'idx_user_role_role',                 COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_user_role_role'
UNION ALL
SELECT 'idx_activity_category',              COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_activity_category'
UNION ALL
SELECT 'idx_signup_audit_user',              COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_signup_audit_user'
UNION ALL
SELECT 'idx_duration_audit_user',            COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_duration_audit_user'
UNION ALL
SELECT 'idx_duration_audit_duration',        COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_duration_audit_duration'
UNION ALL
SELECT 'idx_duration_audit_auditor',         COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_duration_audit_auditor'
UNION ALL
SELECT 'idx_duration_org',                   COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_duration_org'
UNION ALL
SELECT 'idx_notification_user_read',         COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_notification_user_read'
UNION ALL
SELECT 'idx_signup_time',                    COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_signup_time'
UNION ALL
SELECT 'idx_duration_create_time',           COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_duration_create_time';

-- 9.4 数据（期望：两行都是 0）
SELECT 'activity_category 缺 code 的行数' AS item, COUNT(*) AS should_be_zero FROM activity_category WHERE code IS NULL
UNION ALL
SELECT 'activity_category 重复 code 的组数', COUNT(*) FROM (SELECT code FROM activity_category WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1) t;

-- -------------------------------------------------------------
-- 十、更细的核对查询（按需复制执行；故意注释掉，避免污染第九节的输出）
-- -------------------------------------------------------------
-- 1) 6 条分类的编码是否齐全、是否符合预期（期望：6 行，code 互不相同）
-- SELECT id, category_name, code, sort, status FROM activity_category ORDER BY sort;
--
-- 2) 唯一约束是否真的建上（期望：1 行，定义是 UNIQUE (code)）
-- SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
--  WHERE conrelid = 'activity_category'::regclass AND contype = 'u';
--
-- 3) 若第 2 条查不到约束，先看是不是已有重复 code（有约束时本查询恒为 0 行）
-- SELECT code, COUNT(*) FROM activity_category WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1;
--
-- 4) 若团队决定把「文化传播」的编码对齐前端 mock 的 TEACHING：
-- UPDATE activity_category SET code = 'TEACHING' WHERE category_name = '文化传播';
--
-- 5) 全库外键列索引自检：列出每个外键约束，看是否存在以该外键首列开头的索引。
--    执行本脚本后**期望 18 行全部 has_leading_index = true**
--    （org_info.contact_user_id 命中的是 05 建的部分唯一索引，也会是 true）。
--    这里只比较首列，够用来发现「完全没索引」的外键；若外键是多列，需再核对后续列。
-- SELECT c.conrelid::regclass AS table_name,
--        c.conname             AS fk_name,
--        a.attname             AS first_fk_column,
--        EXISTS (SELECT 1 FROM pg_index i
--                 WHERE i.indrelid = c.conrelid
--                   AND i.indisvalid
--                   AND i.indkey[0] = c.conkey[1]) AS has_leading_index
--   FROM pg_constraint c
--   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
--  WHERE c.contype = 'f'
--  ORDER BY has_leading_index, table_name, fk_name;
--
-- 6) 回填结果抽查（活动截止时间/联系方式、时长的组织与分类名、学生性别年级）
-- SELECT id, title, status, deadline, contact FROM volunteer_activity ORDER BY id LIMIT 8;
-- SELECT id, activity_id, org_id, activity_type, proof FROM service_duration ORDER BY id LIMIT 8;
-- SELECT id, student_no, gender, grade FROM student_info ORDER BY id LIMIT 8;
--
-- 7) 回填覆盖率（期望：6 行 remaining 都是 0；reason / proof 两列除外，它们故意不回填）
-- SELECT 'volunteer_activity.deadline 未填' AS item, COUNT(*) AS remaining FROM volunteer_activity WHERE deadline IS NULL AND start_time IS NOT NULL
-- UNION ALL
-- SELECT 'volunteer_activity.contact 未填',  COUNT(*) FROM volunteer_activity WHERE contact  IS NULL AND org_id IS NOT NULL
-- UNION ALL
-- SELECT 'service_duration.org_id 未填',     COUNT(*) FROM service_duration   WHERE org_id   IS NULL
-- UNION ALL
-- SELECT 'service_duration.activity_type 未填', COUNT(*) FROM service_duration WHERE activity_type IS NULL
-- UNION ALL
-- SELECT 'student_info.gender 未填',         COUNT(*) FROM student_info       WHERE gender   IS NULL
-- UNION ALL
-- SELECT 'student_info.grade 未填',          COUNT(*) FROM student_info       WHERE grade    IS NULL;
