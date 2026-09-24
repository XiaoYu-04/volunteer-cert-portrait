-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 12：活动图片与少量演示数据（增量、可重复执行）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 数据规模刻意保持很小：
--   新增 5 个账号（3 学生 + 1 组织管理员 + 1 学校管理员）
--   新增 1 个已审核组织
--   新增 3 个已发布活动，每个活动 1 张封面 + 1 条图文说明
--
-- 口令均为 123456，库中存 BCrypt 密文。
-- 图片文件位于 volunteer-cert-portrait-server/uploads/demo/activities/，
-- 数据库只保存 /uploads/... 地址。
-- =============================================================

SET client_encoding = 'UTF8';

DO $$
BEGIN
    IF (SELECT COUNT(*)
          FROM sys_role
         WHERE deleted = 0
           AND role_code IN ('STUDENT', 'ORG_ADMIN', 'SCHOOL_ADMIN')) < 3 THEN
        RAISE EXCEPTION '[12] 缺少 STUDENT / ORG_ADMIN / SCHOOL_ADMIN 角色，请先执行 03_init_data.sql';
    END IF;
END $$;

-- =============================================================
-- 一、5 个演示账号
-- =============================================================
WITH seed(username, real_name, phone, email) AS (
    VALUES
        ('demo_stu_01',    '林知夏', '13800010001', 'demo.stu01@example.com'),
        ('demo_stu_02',    '周予安', '13800010002', 'demo.stu02@example.com'),
        ('demo_stu_03',    '陈星野', '13800010003', 'demo.stu03@example.com'),
        ('demo_org_01',    '赵明远', '13800010004', 'demo.org01@example.com'),
        ('demo_school_01', '顾南枝', '13800010005', 'demo.school01@example.com')
)
INSERT INTO sys_user (username, password, real_name, phone, email, status)
SELECT s.username,
       '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa',
       s.real_name,
       s.phone,
       s.email,
       1
  FROM seed s
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_user u WHERE u.username = s.username
 );

WITH seed(username, role_code) AS (
    VALUES
        ('demo_stu_01',    'STUDENT'),
        ('demo_stu_02',    'STUDENT'),
        ('demo_stu_03',    'STUDENT'),
        ('demo_org_01',    'ORG_ADMIN'),
        ('demo_school_01', 'SCHOOL_ADMIN')
)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
  JOIN sys_role r ON r.role_code = s.role_code AND r.deleted = 0
 WHERE NOT EXISTS (
       SELECT 1
         FROM sys_user_role ur
        WHERE ur.user_id = u.id
          AND ur.role_id = r.id
 );

-- =============================================================
-- 二、3 个学生档案
-- =============================================================
WITH seed(username, student_no, college, major, class_name) AS (
    VALUES
        ('demo_stu_01', '20260001', '计算机学院',   '软件工程',       '软件2601'),
        ('demo_stu_02', '20260002', '计算机学院',   '网络工程',       '网工2601'),
        ('demo_stu_03', '20260003', '计算机学院',   '数据科学与大数据技术', '数据2601')
)
INSERT INTO student_info (user_id, student_no, college, major, class_name, total_duration, public_welfare_level)
SELECT u.id, s.student_no, s.college, s.major, s.class_name, 0, '普通志愿者'
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
 WHERE NOT EXISTS (
       SELECT 1 FROM student_info si WHERE si.user_id = u.id
 );

UPDATE student_info si
   SET college = '计算机学院'
  FROM sys_user u
 WHERE si.user_id = u.id
   AND u.username LIKE 'demo_stu_%'
   AND u.deleted = 0
   AND si.deleted = 0;

-- =============================================================
-- 三、1 个已审核组织，供演示组织管理员使用
-- =============================================================
WITH admin AS (
    SELECT id, real_name, phone, email
      FROM sys_user
     WHERE username = 'demo_org_01'
       AND deleted = 0
     LIMIT 1
)
INSERT INTO org_info (contact_user_id, org_name, org_type, contact_name, phone, email, description, status, college)
SELECT admin.id,
       '晨曦青年志愿服务队',
       '社区组织',
       admin.real_name,
       admin.phone,
       admin.email,
       '面向社区与校园开展助老、环保和青少年陪伴服务。',
       'APPROVED',
       '计算机学院'
  FROM admin
 WHERE NOT EXISTS (
       SELECT 1
         FROM org_info o
        WHERE o.contact_user_id = admin.id
          AND o.deleted = 0
 );

-- 基础库若已有组织但未回填学院，补成与学生数据一致的演示值。
UPDATE org_info o
   SET college = '计算机学院'
  FROM sys_user u
 WHERE o.contact_user_id = u.id
   AND u.username IN ('org_admin', 'demo_org_01')
   AND (o.college IS NULL OR btrim(o.college) = '');

-- 基础学生档案若未生成等级，补齐后再生成画像，保证一致性自检可通过。
UPDATE student_info
   SET public_welfare_level = '普通志愿者'
 WHERE deleted = 0
   AND total_duration < 3
   AND (public_welfare_level IS NULL OR btrim(public_welfare_level) = '');

-- =============================================================
-- 四、3 个已发布活动
-- =============================================================
WITH demo_org AS (
    SELECT o.id
      FROM org_info o
      JOIN sys_user u ON u.id = o.contact_user_id
     WHERE u.username = 'demo_org_01'
       AND u.deleted = 0
       AND o.deleted = 0
     ORDER BY o.id
     LIMIT 1
),
seed(title, category_name, offset_days, duration, location, max_count, description) AS (
    VALUES
        ('社区敬老陪伴日',   '助老服务', 3,  3.0, '幸福里社区敬老院', 20,
         '陪伴社区老人聊天、散步，并协助整理活动室。'),
        ('校园河道清洁行动', '环保公益', 7,  3.0, '校园东侧河道',     35,
         '清理河道沿线垃圾，记录并宣传垃圾分类知识。'),
        ('社区儿童阅读课堂', '社区服务', 10, 2.0, '阳光社区图书室',   18,
         '陪伴社区儿童阅读绘本，开展小组分享活动。')
)
INSERT INTO volunteer_activity
    (title, category_id, org_id, start_time, end_time, location, max_count,
     signed_count, duration, status, cover, description, deadline, contact)
SELECT s.title,
       c.id,
       o.id,
       CURRENT_TIMESTAMP + (s.offset_days || ' days')::interval,
       CURRENT_TIMESTAMP + (s.offset_days || ' days')::interval
           + (s.duration * INTERVAL '1 hour'),
       s.location,
       s.max_count,
       0,
       s.duration,
       'PUBLISHED',
       NULL,
       s.description,
       CURRENT_TIMESTAMP + (s.offset_days || ' days')::interval - INTERVAL '1 day',
       '赵明远 13800010004'
  FROM seed s
  JOIN demo_org o ON TRUE
  JOIN activity_category c
    ON c.category_name = s.category_name
   AND c.deleted = 0
 WHERE NOT EXISTS (
       SELECT 1
         FROM volunteer_activity a
        WHERE a.title = s.title
          AND a.org_id = o.id
          AND a.deleted = 0
 );

-- =============================================================
-- 五、活动图片元数据与封面
-- =============================================================
WITH demo_org AS (
    SELECT o.id
      FROM org_info o
      JOIN sys_user u ON u.id = o.contact_user_id
     WHERE u.username = 'demo_org_01'
       AND u.deleted = 0
       AND o.deleted = 0
     ORDER BY o.id
     LIMIT 1
),
demo(title, file_url, file_name, file_size, caption) AS (
    VALUES
        ('社区敬老陪伴日',
         '/uploads/demo/activities/community-elder-care.png',
         'community-elder-care.png', 2295606,
         '志愿者陪伴社区老人，耐心倾听并协助整理活动室。'),
        ('校园河道清洁行动',
         '/uploads/demo/activities/river-cleanup.png',
         'river-cleanup.png', 2908143,
         '志愿者沿校园河道清理垃圾，用实际行动宣传环保理念。'),
        ('社区儿童阅读课堂',
         '/uploads/demo/activities/children-reading.png',
         'children-reading.png', 2155792,
         '志愿者在社区图书室陪伴儿童阅读，开展轻松的分享活动。')
),
target AS (
    SELECT a.id AS activity_id, d.file_url, d.file_name, d.file_size, d.caption
      FROM demo d
      JOIN volunteer_activity a
        ON a.title = d.title
       AND a.deleted = 0
      JOIN demo_org o ON o.id = a.org_id
)
DELETE FROM attachment a
 USING target t
 WHERE a.biz_type = 'ACTIVITY'
   AND a.biz_id = t.activity_id;

WITH demo_org AS (
    SELECT o.id
      FROM org_info o
      JOIN sys_user u ON u.id = o.contact_user_id
     WHERE u.username = 'demo_org_01'
       AND u.deleted = 0
       AND o.deleted = 0
     ORDER BY o.id
     LIMIT 1
),
demo(title, file_url, file_name, file_size, caption) AS (
    VALUES
        ('社区敬老陪伴日',
         '/uploads/demo/activities/community-elder-care.png',
         'community-elder-care.png', 2295606,
         '志愿者陪伴社区老人，耐心倾听并协助整理活动室。'),
        ('校园河道清洁行动',
         '/uploads/demo/activities/river-cleanup.png',
         'river-cleanup.png', 2908143,
         '志愿者沿校园河道清理垃圾，用实际行动宣传环保理念。'),
        ('社区儿童阅读课堂',
         '/uploads/demo/activities/children-reading.png',
         'children-reading.png', 2155792,
         '志愿者在社区图书室陪伴儿童阅读，开展轻松的分享活动。')
),
target AS (
    SELECT a.id AS activity_id, d.file_url, d.file_name, d.file_size, d.caption
      FROM demo d
      JOIN volunteer_activity a
        ON a.title = d.title
       AND a.deleted = 0
      JOIN demo_org o ON o.id = a.org_id
)
INSERT INTO attachment
    (biz_type, biz_id, file_name, file_url, file_size, content_type, caption, sort_order)
SELECT 'ACTIVITY',
       t.activity_id,
       t.file_name,
       t.file_url,
       t.file_size,
       'image/png',
       t.caption,
       0
  FROM target t;

WITH demo_org AS (
    SELECT id
      FROM org_info
     WHERE org_name = '晨曦青年志愿服务队'
       AND deleted = 0
     ORDER BY id
     LIMIT 1
),
demo(title, cover) AS (
    VALUES
        ('社区敬老陪伴日',   '/uploads/demo/activities/community-elder-care.png'),
        ('校园河道清洁行动', '/uploads/demo/activities/river-cleanup.png'),
        ('社区儿童阅读课堂', '/uploads/demo/activities/children-reading.png')
)
UPDATE volunteer_activity a
   SET cover = d.cover,
       update_time = CURRENT_TIMESTAMP
  FROM demo d, demo_org o
 WHERE a.title = d.title
   AND o.id = a.org_id
   AND a.deleted = 0;

-- =============================================================
-- 六、补齐少量画像，覆盖 8 类演示标签
-- =============================================================
WITH seed(student_no, tags) AS (
    VALUES
        ('S000011',  '热心志愿者,长期坚持型'),
        ('20260001', '校园服务型,社区服务型'),
        ('20260002', '环保行动型,大型活动型'),
        ('20260003', '助老服务型,文化传播型')
)
INSERT INTO student_profile
    (student_id, total_activities, total_duration, category_preference, tags, portrait_desc)
SELECT si.id,
       0,
       COALESCE(si.total_duration, 0),
       NULL,
       s.tags,
       '演示账号公益画像。'
  FROM seed s
  JOIN student_info si
    ON si.student_no = s.student_no
   AND si.deleted = 0
 WHERE NOT EXISTS (
       SELECT 1
         FROM student_profile sp
        WHERE sp.student_id = si.id
 );

-- =============================================================
-- 七、自检
-- =============================================================
SELECT COUNT(*) AS demo_users
  FROM sys_user
 WHERE username LIKE 'demo_%'
   AND deleted = 0;

SELECT COUNT(*) AS demo_students
  FROM student_info si
  JOIN sys_user u ON u.id = si.user_id
 WHERE u.username LIKE 'demo_stu_%'
   AND u.deleted = 0
   AND si.deleted = 0;

SELECT COUNT(*) AS demo_activities
  FROM volunteer_activity a
  JOIN org_info o ON o.id = a.org_id
  JOIN sys_user u ON u.id = o.contact_user_id
 WHERE u.username = 'demo_org_01'
   AND a.title IN ('社区敬老陪伴日', '校园河道清洁行动', '社区儿童阅读课堂')
   AND a.deleted = 0
   AND o.deleted = 0
   AND u.deleted = 0;

SELECT COUNT(*) AS demo_activity_images
  FROM attachment a
  JOIN volunteer_activity v
    ON v.id = a.biz_id
   AND v.deleted = 0
  JOIN org_info o
    ON o.id = v.org_id
   AND o.deleted = 0
  JOIN sys_user u
    ON u.id = o.contact_user_id
   AND u.deleted = 0
 WHERE a.biz_type = 'ACTIVITY'
   AND u.username = 'demo_org_01'
   AND v.title IN ('社区敬老陪伴日', '校园河道清洁行动', '社区儿童阅读课堂');

SELECT COUNT(*) AS missing_role_bindings
  FROM sys_user u
 WHERE u.username LIKE 'demo_%'
   AND u.deleted = 0
   AND NOT EXISTS (
       SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id
   );

SELECT COUNT(*) AS demo_profiles
  FROM student_profile sp
  JOIN student_info si ON si.id = sp.student_id
 WHERE si.student_no IN ('S000011', '20260001', '20260002', '20260003');
