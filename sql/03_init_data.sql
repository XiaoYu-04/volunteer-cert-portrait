-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 3/4：基础初始化数据
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 本脚本只写入【系统跑起来必需】的基础数据：
--   角色、三个角色账号、学生档案、1 个已通过审核的组织、活动分类、数据字典
-- 不含活动/报名/时长等业务演示数据 —— 那些在 04_demo_data.sql 里（可选）
--
-- 默认账号（密码均为明文 123456，接入加密后请自行替换）：
--   学校管理员：admin      / 123456
--   组织管理员：org_admin  / 123456
--   学生：      student    / 123456
--
-- ⚠️ 明文密码仅供本地开发与演示。接入 BCrypt 后，必须把 sys_user.password
--    换成加密后的密文，否则登录校验会失败。
-- =============================================================

SET client_encoding = 'UTF8';

-- =============================================================
-- 一、角色
-- =============================================================
INSERT INTO sys_role (id, role_code, role_name, remark) VALUES
(1, 'STUDENT',      '学生',       '学生角色'),
(2, 'ORG_ADMIN',    '组织管理员', '志愿组织管理员'),
(3, 'SCHOOL_ADMIN', '学校管理员', '学校管理员');

-- =============================================================
-- 二、账号（三个角色各一个）
-- =============================================================
INSERT INTO sys_user (id, username, password, real_name, phone, email, status) VALUES
(1, 'admin',     '123456', '学校管理员', '13800000001', 'admin@example.com',   1),
(2, 'org_admin', '123456', '组织管理员', '13800000002', 'org@example.com',     1),
(3, 'student',   '123456', '张同学',     '13800000003', 'student@example.com', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 3),   -- admin     → 学校管理员
(2, 2),   -- org_admin → 组织管理员
(3, 1);   -- student   → 学生

-- =============================================================
-- 三、学生档案（对应 student 账号）
--     total_duration 初始为 0，公开等级暂用文档中出现的唯一取值
-- =============================================================
INSERT INTO student_info (id, user_id, student_no, college, major, class_name, total_duration, public_welfare_level) VALUES
(1, 3, '20230001', '计算机学院', '软件技术', '软件2301', 0, '普通志愿者');

-- =============================================================
-- 四、志愿组织（1 个，已通过审核，供 org_admin 账号使用）
-- =============================================================
INSERT INTO org_info (id, contact_user_id, org_name, org_type, contact_name, phone, email, description, status) VALUES
(1, 2, '计算机学院青年志愿者协会', '学院组织', '组织管理员', '13800000002', 'org@example.com',
 '计算机学院下属志愿服务组织，长期开展校园服务与社区帮扶活动。', 'APPROVED');

-- =============================================================
-- 五、活动分类
-- =============================================================
INSERT INTO activity_category (id, category_name, sort, status) VALUES
(1, '校园服务', 1, 1),
(2, '社区服务', 2, 1),
(3, '环保公益', 3, 1),
(4, '大型赛事', 4, 1),
(5, '助老服务', 5, 1),
(6, '文化传播', 6, 1);

-- =============================================================
-- 六、数据字典（状态英文码 → 中文展示值）
--     前 4 类与最初生成的脚本一致；后 4 类为补齐（原脚本遗漏，
--     导致组织审核状态、通知类型等无法翻译）
-- =============================================================

-- 活动状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('activity_status', 'DRAFT',     '草稿',   1),
('activity_status', 'PUBLISHED', '已发布', 2),
('activity_status', 'CLOSED',    '已结束', 3),
('activity_status', 'CANCELED',  '已取消', 4);

-- 报名状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('signup_status', 'PENDING',   '待审核', 1),
('signup_status', 'APPROVED',  '已通过', 2),
('signup_status', 'REJECTED',  '已驳回', 3),
('signup_status', 'CANCELED',  '已取消', 4),
('signup_status', 'COMPLETED', '已完成', 5);

-- 签到状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('attendance_status', 'NOT_SIGNED', '未签到', 1),
('attendance_status', 'SIGNED_IN',  '已签到', 2),
('attendance_status', 'SIGNED_OUT', '已签退', 3),
('attendance_status', 'ABNORMAL',   '异常',   4),
('attendance_status', 'ABSENT',     '缺勤',   5);

-- 时长状态
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('duration_status', 'PENDING_SUBMIT', '待提交', 1),
('duration_status', 'PENDING_AUDIT',  '待审核', 2),
('duration_status', 'APPROVED',       '已通过', 3),
('duration_status', 'REJECTED',       '已驳回', 4);

-- 组织审核状态（原脚本遗漏）
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('org_status', 'PENDING',  '待审核', 1),
('org_status', 'APPROVED', '已通过', 2),
('org_status', 'REJECTED', '已驳回', 3);

-- 通知类型（原脚本遗漏）
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('notification_type', 'SIGNUP',   '报名结果', 1),
('notification_type', 'DURATION', '时长审核', 2),
('notification_type', 'SYSTEM',   '系统公告', 3);

-- 时长审核动作（原脚本遗漏；注意与 duration_status 的 APPROVED/REJECTED 不同形）
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('audit_action', 'SUBMIT',  '提交', 1),
('audit_action', 'APPROVE', '通过', 2),
('audit_action', 'REJECT',  '驳回', 3);

-- 附件业务类型（原脚本遗漏）
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort) VALUES
('attachment_biz_type', 'ACTIVITY', '活动封面', 1),
('attachment_biz_type', 'ORG',      '组织资质', 2),
('attachment_biz_type', 'AVATAR',   '用户头像', 3);

-- =============================================================
-- 七、操作日志（留一条初始化记录，便于确认脚本已执行）
-- =============================================================
INSERT INTO operation_log (user_id, username, operation, method, params, ip) VALUES
(1, 'admin', '初始化系统基础数据', 'sql/03_init_data.sql', NULL, '127.0.0.1');

-- =============================================================
-- 八、修复自增序列
--     上面显式指定了 id（1、2、3…），不会推进 BIGSERIAL 的序列，
--     若不修复，后续应用插入会从 1 开始从而主键冲突。
--     setval 第三个参数 true 表示下一个值取 max+1。
-- =============================================================
SELECT setval(pg_get_serial_sequence('sys_role',          'id'), COALESCE((SELECT MAX(id) FROM sys_role),          1));
SELECT setval(pg_get_serial_sequence('sys_user',          'id'), COALESCE((SELECT MAX(id) FROM sys_user),          1));
SELECT setval(pg_get_serial_sequence('sys_user_role',     'id'), COALESCE((SELECT MAX(id) FROM sys_user_role),     1));
SELECT setval(pg_get_serial_sequence('student_info',      'id'), COALESCE((SELECT MAX(id) FROM student_info),      1));
SELECT setval(pg_get_serial_sequence('org_info',          'id'), COALESCE((SELECT MAX(id) FROM org_info),          1));
SELECT setval(pg_get_serial_sequence('activity_category', 'id'), COALESCE((SELECT MAX(id) FROM activity_category), 1));
SELECT setval(pg_get_serial_sequence('operation_log',     'id'), COALESCE((SELECT MAX(id) FROM operation_log),     1));

-- 未显式指定 id 的表（sys_dict、notification 等）由序列自动推进，无需修复。

-- =============================================================
-- 验证
-- =============================================================
-- SELECT (SELECT COUNT(*) FROM sys_role)          AS roles,
--        (SELECT COUNT(*) FROM sys_user)          AS users,
--        (SELECT COUNT(*) FROM activity_category) AS categories,
--        (SELECT COUNT(*) FROM sys_dict)          AS dict_items;
-- 期望：3 / 3 / 6 / 30