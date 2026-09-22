-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 5/5：后端落地所需的补列脚本
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 【为什么需要这个脚本】
-- 前端 28 个页面已经把字段契约写死（见 docs/待办清单.md 的 B16 审计发现），
-- 而 02_schema.sql 建的表缺若干列。缺的列无法在应用层凭空造出来，必须扩表。
--
-- 【本脚本只做加法】
--   全部是 ADD COLUMN IF NOT EXISTS 与 INSERT ... ON CONFLICT DO NOTHING，
--   不改列类型、不删列、不动已有数据，因此可以安全地重复执行。
--
-- 【执行顺序】在 04_demo_data.sql 之后执行（若已导入演示数据）。
--
-- 【为什么不做成 Flyway 迁移】
--   spring.flyway.enabled 目前为 false（见 application.yml 的说明），
--   建表以本目录脚本为准，避免同一份 DDL 维护两遍而漂移。
-- =============================================================

SET client_encoding = 'UTF8';

-- -------------------------------------------------------------
-- 一、sys_dict 补 tone 列
-- -------------------------------------------------------------
-- 前端 stores/dict.js 的 load() 会用接口数据**整体替换**本地静态字典，
-- 而替换时写的是 item.tone || 'mute'。也就是说：
--   字典接口不返回 tone → 所有状态标签的颜色都会退化成灰色。
-- 这不是「可选优化」，是必须补的字段。
-- 取值与前端 DICT_DEFS 逐条对齐：mute 灰 / ok 绿 / warn 橙 / bad 红 / info 蓝。
ALTER TABLE sys_dict ADD COLUMN IF NOT EXISTS tone VARCHAR(20) DEFAULT 'mute';

COMMENT ON COLUMN sys_dict.tone IS '标签色调：mute灰 / ok绿 / warn橙 / bad红 / info蓝；与前端 StatusTag 的取值一致';

UPDATE sys_dict SET tone = 'mute' WHERE tone IS NULL;

-- 色调必须按 (dict_type, dict_key) 精确指定，不能只按 dict_key 匹配：
-- 不同字典类型里有同名键（如 CANCELED 同时属于 activity_status 与 signup_status），
-- 只按 dict_key 更新会把它们刷成同一种色调，而前端的取值并不相同 ——
-- activity_status.CANCELED 是 bad（红），signup_status.CANCELED 是 mute（灰）。
-- 这类错配不会报错，只会在页面上表现为"这个标签的颜色跟别处不一样"，极难定位。
-- 下面的映射与前端 stores/dict.js 的 DICT_DEFS 逐条一致，改一处要同时改另一处。
UPDATE sys_dict d
   SET tone = v.tone
  FROM (VALUES
        -- 活动状态
        ('activity_status',    'DRAFT',          'mute'),
        ('activity_status',    'PUBLISHED',      'ok'),
        ('activity_status',    'CLOSED',         'info'),
        ('activity_status',    'CANCELED',       'bad'),
        -- 报名状态
        ('signup_status',      'PENDING',        'warn'),
        ('signup_status',      'APPROVED',       'ok'),
        ('signup_status',      'REJECTED',       'bad'),
        ('signup_status',      'CANCELED',       'mute'),
        ('signup_status',      'COMPLETED',      'info'),
        -- 签到状态
        ('attendance_status',  'NOT_SIGNED',     'mute'),
        ('attendance_status',  'SIGNED_IN',      'info'),
        ('attendance_status',  'SIGNED_OUT',     'ok'),
        ('attendance_status',  'ABNORMAL',       'warn'),
        ('attendance_status',  'ABSENT',         'bad'),
        -- 服务时长状态
        ('duration_status',    'PENDING_SUBMIT', 'mute'),
        ('duration_status',    'PENDING_AUDIT',  'warn'),
        ('duration_status',    'APPROVED',       'ok'),
        ('duration_status',    'REJECTED',       'bad'),
        -- 组织资质状态
        ('org_status',         'PENDING',        'warn'),
        ('org_status',         'APPROVED',       'ok'),
        ('org_status',         'REJECTED',       'bad'),
        ('org_status',         'DISABLED',       'mute'),
        -- 账号状态
        ('user_status',        'ACTIVE',         'ok'),
        ('user_status',        'DISABLED',       'mute'),
        -- 审核动作。注意键名是 APPROVE / REJECT，与状态的 APPROVED / REJECTED 不同形，
        -- 两者在同一个页面上并存（状态列与动作列），色调不一致会显得很随意。
        ('audit_action',       'SUBMIT',         'info'),
        ('audit_action',       'APPROVE',        'ok'),
        ('audit_action',       'REJECT',         'bad'),
        -- 通知类型
        ('notification_type',  'SIGNUP',         'ok'),
        ('notification_type',  'DURATION',       'warn'),
        ('notification_type',  'SYSTEM',         'info'),
        -- 附件业务类型（前端未定义本地静态项，色调统一取灰即可）
        ('attachment_biz_type','ACTIVITY',       'mute'),
        ('attachment_biz_type','ORG',            'mute'),
        ('attachment_biz_type','AVATAR',         'mute')
       ) AS v(dict_type, dict_key, tone)
 WHERE d.dict_type = v.dict_type
   AND d.dict_key  = v.dict_key;

-- -------------------------------------------------------------
-- 二、sys_dict 补 user_status 字典
-- -------------------------------------------------------------
-- 待办项 A9：sys_user.status 在库里是 SMALLINT（1/0），而前端用的是
-- ACTIVE / DISABLED 字符串。后端在服务层做翻译（UserStatusEnum 同时带英文码与 dbValue），
-- 但字典种子必须先有，否则前端 user_status 的下拉与标签取不到值。
-- 若将来 A9 拍板把列改成英文码，本字典无需改动。
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort, status, tone) VALUES
('user_status', 'ACTIVE',   '正常',   1, 1, 'ok'),
('user_status', 'DISABLED', '已停用', 2, 1, 'mute')
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- 组织「停用」状态：前端 DICT_DEFS 的 org_status 有 DISABLED，库里缺这一条
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort, status, tone) VALUES
('org_status', 'DISABLED', '已停用', 4, 1, 'mute')
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- -------------------------------------------------------------
-- 三、sys_user 补 last_login_at
-- -------------------------------------------------------------
-- 用户管理页有一列「最近登录」。原表没有该列，登录接口也就无处记录。
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

COMMENT ON COLUMN sys_user.last_login_at IS '最近一次登录成功的时间；由登录接口写入，仅用于展示与审计';

-- -------------------------------------------------------------
-- 四、notification 补 source / is_top / batch_no
-- -------------------------------------------------------------
-- 通知列表页要显示「来源」与「置顶」，原表都没有。
-- 注意列名不能叫 from —— 那是 SQL 关键字，故用 source，出参再映射成前端的 from。
ALTER TABLE notification ADD COLUMN IF NOT EXISTS source VARCHAR(50);
ALTER TABLE notification ADD COLUMN IF NOT EXISTS is_top BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN notification.source IS '通知来源（前端字段名 from）：如 校团委 / 系统管理员；SQL 关键字冲突故列名取 source';
COMMENT ON COLUMN notification.is_top IS '是否置顶：置顶公告在学生端列表排在前面';

-- 公告是「群发」：一条公告按收件人各插一行，这样 is_read 才是每人独立的状态。
-- 但删除时管理员只持有自己那一行的 id，必须靠 batch_no 把同批次的全部收件人行一起删掉，
-- 否则删掉的只是自己的一份，学生端仍能看到。
ALTER TABLE notification ADD COLUMN IF NOT EXISTS batch_no VARCHAR(36);

COMMENT ON COLUMN notification.batch_no IS '群发批次号（UUID）：同一次公告群发插入的所有收件人行共用一个值，删除时按它整批删除；非群发（如报名结果通知）为 NULL';

CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notification (user_id, is_read);

-- -------------------------------------------------------------
-- 五、operation_log 补 module / action / target / result / cost
-- -------------------------------------------------------------
-- 日志页 8 列中，role / module / target / result 四列原表全无（B16）。
-- 其中 role 由 user_id 关联角色推导，不落列；其余四列落列。
-- 原 operation 列继续保存「模块 - 动作」的完整描述，保持兼容。
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS module VARCHAR(50);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS action VARCHAR(50);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS target VARCHAR(200);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS result VARCHAR(20);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS cost   BIGINT;

COMMENT ON COLUMN operation_log.module IS '所属模块：必须是日志页筛选项里的中文名（时长认证/志愿活动/活动报名/用户与权限/签到签退/志愿组织/认证），前端按它全等筛选';
COMMENT ON COLUMN operation_log.action IS '动作描述，如 新增用户 / 审核通过';
COMMENT ON COLUMN operation_log.target IS '操作对象描述；当前切面尚未采集，留空（后续给 @OperationLog 加 target 属性后填充）';
COMMENT ON COLUMN operation_log.result IS '执行结果：SUCCESS成功 / FAIL失败';
COMMENT ON COLUMN operation_log.cost   IS '业务方法耗时（毫秒）';

CREATE INDEX IF NOT EXISTS idx_operation_log_module ON operation_log (module);
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time ON operation_log (create_time DESC);

-- -------------------------------------------------------------
-- 六、回填已有数据
-- -------------------------------------------------------------
UPDATE operation_log
   SET module = '认证', action = '初始化数据', result = 'SUCCESS'
 WHERE module IS NULL;

UPDATE notification SET source = '系统管理员' WHERE source IS NULL;
UPDATE notification SET is_top = FALSE      WHERE is_top IS NULL;

-- -------------------------------------------------------------
-- 七、自检：确认所有列都已就位
-- -------------------------------------------------------------
SELECT 'sys_dict.tone' AS item, COUNT(*) AS ok FROM information_schema.columns WHERE table_name = 'sys_dict' AND column_name = 'tone'
UNION ALL
SELECT 'sys_user.last_login_at', COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_user' AND column_name = 'last_login_at'
UNION ALL
SELECT 'notification.source', COUNT(*) FROM information_schema.columns WHERE table_name = 'notification' AND column_name = 'source'
UNION ALL
SELECT 'notification.is_top', COUNT(*) FROM information_schema.columns WHERE table_name = 'notification' AND column_name = 'is_top'
UNION ALL
SELECT 'notification.batch_no', COUNT(*) FROM information_schema.columns WHERE table_name = 'notification' AND column_name = 'batch_no'
UNION ALL
SELECT 'operation_log.module', COUNT(*) FROM information_schema.columns WHERE table_name = 'operation_log' AND column_name = 'module'
UNION ALL
SELECT 'operation_log.result', COUNT(*) FROM information_schema.columns WHERE table_name = 'operation_log' AND column_name = 'result'
UNION ALL
SELECT 'sys_dict.user_status 种子', COUNT(*) FROM sys_dict WHERE dict_type = 'user_status';

-- 期望：前 7 行 ok = 1，最后一行 ok = 2
