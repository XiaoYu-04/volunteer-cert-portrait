-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 2/4：建表脚本（16 张表 + 表/字段注释 + 索引）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 全局约定（源自知识库《全局技术规范》）：
--   1. 主键统一 id BIGSERIAL PRIMARY KEY（数据库自增，非雪花 ID）
--      对应 application.yml 中 mybatis-plus.global-config.db-config.id-type: auto
--   2. 命名统一 snake_case
--   3. 状态字段统一 VARCHAR 存【英文大写枚举码】，中文由 sys_dict 翻译
--   4. 时间字段统一 create_time / update_time，类型 TIMESTAMP
--   5. 时长字段统一 NUMERIC(10,1)，单位【小时】
--   6. 逻辑删除统一 deleted SMALLINT（0未删除 / 1已删除）
--      —— 仅用于"业务数据需要保留痕迹"的表；流水/日志/关联表不加（见各表说明）
--
-- 执行顺序：01_create_database.sql → 本脚本 → 03_init_data.sql → [04_demo_data.sql]
-- =============================================================

SET client_encoding = 'UTF8';

-- =============================================================
-- 清理旧表（使脚本可重复执行）
-- 依赖倒序删除，CASCADE 兜底；【会连同数据一起删除，生产环境切勿执行】
-- =============================================================
DROP TABLE IF EXISTS attachment         CASCADE;
DROP TABLE IF EXISTS operation_log      CASCADE;
DROP TABLE IF EXISTS sys_dict           CASCADE;
DROP TABLE IF EXISTS notification       CASCADE;
DROP TABLE IF EXISTS student_profile    CASCADE;
DROP TABLE IF EXISTS duration_audit     CASCADE;
DROP TABLE IF EXISTS service_duration   CASCADE;
DROP TABLE IF EXISTS attendance_record  CASCADE;
DROP TABLE IF EXISTS activity_signup    CASCADE;
DROP TABLE IF EXISTS volunteer_activity CASCADE;
DROP TABLE IF EXISTS activity_category  CASCADE;
DROP TABLE IF EXISTS org_info           CASCADE;
DROP TABLE IF EXISTS student_info       CASCADE;
DROP TABLE IF EXISTS sys_user_role      CASCADE;
DROP TABLE IF EXISTS sys_role           CASCADE;
DROP TABLE IF EXISTS sys_user           CASCADE;


-- =============================================================
-- 一、系统域（vcp-system）—— 8 张表
-- =============================================================

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGSERIAL    PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(50),
    phone       VARCHAR(20),
    email       VARCHAR(100),
    avatar      VARCHAR(255),
    status      SMALLINT     DEFAULT 1,
    deleted     SMALLINT     DEFAULT 0,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_user             IS '用户表';
COMMENT ON COLUMN sys_user.username    IS '用户名（登录账号，唯一）';
COMMENT ON COLUMN sys_user.password    IS '密码：BCrypt 密文（$2a$10$ 开头的 60 字符），明文口令不落库；比对见 vcp-framework 的 PasswordUtils';
COMMENT ON COLUMN sys_user.real_name   IS '真实姓名';
COMMENT ON COLUMN sys_user.phone       IS '手机号';
COMMENT ON COLUMN sys_user.email       IS '邮箱';
COMMENT ON COLUMN sys_user.avatar      IS '头像地址';
COMMENT ON COLUMN sys_user.status      IS '状态：1启用，0禁用';
COMMENT ON COLUMN sys_user.deleted     IS '逻辑删除：0未删除，1已删除';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';

-- -------------------------------------------------------------
-- 2. 角色表
-- -------------------------------------------------------------
CREATE TABLE sys_role (
    id          BIGSERIAL    PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    role_name   VARCHAR(50)  NOT NULL,
    remark      VARCHAR(255),
    deleted     SMALLINT     DEFAULT 0,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_role           IS '角色表';
COMMENT ON COLUMN sys_role.role_code IS '角色编码：STUDENT学生，ORG_ADMIN组织管理员，SCHOOL_ADMIN学校管理员';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.remark    IS '备注';
COMMENT ON COLUMN sys_role.deleted   IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 3. 用户角色关联表
--    说明：关联表按物理删除设计，不加 deleted / update_time
-- -------------------------------------------------------------
CREATE TABLE sys_user_role (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    role_id     BIGINT    NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT uk_user_role      UNIQUE (user_id, role_id)
);

COMMENT ON TABLE  sys_user_role      IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID → sys_user.id';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID → sys_role.id';

-- -------------------------------------------------------------
-- 4. 学生信息表
-- -------------------------------------------------------------
CREATE TABLE student_info (
    id                   BIGSERIAL    PRIMARY KEY,
    user_id              BIGINT       NOT NULL UNIQUE,
    student_no           VARCHAR(30)  NOT NULL UNIQUE,
    college              VARCHAR(100),
    major                VARCHAR(100),
    class_name           VARCHAR(100),
    total_duration       NUMERIC(10,1) DEFAULT 0,
    public_welfare_level VARCHAR(50),
    deleted              SMALLINT     DEFAULT 0,
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

COMMENT ON TABLE  student_info                      IS '学生信息表';
COMMENT ON COLUMN student_info.user_id              IS '关联用户ID → sys_user.id（一个用户对应一个学生档案）';
COMMENT ON COLUMN student_info.student_no           IS '学号（唯一）';
COMMENT ON COLUMN student_info.college              IS '学院';
COMMENT ON COLUMN student_info.major                IS '专业';
COMMENT ON COLUMN student_info.class_name           IS '班级';
COMMENT ON COLUMN student_info.total_duration       IS '累计有效志愿时长（小时）；由 service_duration 审核通过的数据汇总而来，是学院排名与画像的依据';
COMMENT ON COLUMN student_info.public_welfare_level IS '公益等级；【档位与阈值尚未确定，见 sql/README.md 待定项】';
COMMENT ON COLUMN student_info.deleted              IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 5. 数据字典表
-- -------------------------------------------------------------
CREATE TABLE sys_dict (
    id          BIGSERIAL    PRIMARY KEY,
    dict_type   VARCHAR(50)  NOT NULL,
    dict_key    VARCHAR(50)  NOT NULL,
    dict_value  VARCHAR(100) NOT NULL,
    sort        INT          DEFAULT 0,
    status      SMALLINT     DEFAULT 1,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dict_type_key UNIQUE (dict_type, dict_key)
);

COMMENT ON TABLE  sys_dict           IS '数据字典表：状态英文码 → 中文展示值';
COMMENT ON COLUMN sys_dict.dict_type IS '字典类型：activity_status活动状态，signup_status报名状态，attendance_status签到状态，duration_status时长状态，org_status组织审核状态，notification_type通知类型，audit_action审核动作，attachment_biz_type附件业务类型';
COMMENT ON COLUMN sys_dict.dict_key  IS '字典键（入库的英文枚举码）';
COMMENT ON COLUMN sys_dict.dict_value IS '字典值（前端展示的中文）';
COMMENT ON COLUMN sys_dict.sort      IS '同类型内的排序';
COMMENT ON COLUMN sys_dict.status    IS '状态：1启用，0禁用';

-- -------------------------------------------------------------
-- 6. 通知表
--    说明：通知用 is_read 表达"已读"，不做逻辑删除
-- -------------------------------------------------------------
CREATE TABLE notification (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200),
    content     TEXT,
    type        VARCHAR(50),
    is_read     BOOLEAN      DEFAULT FALSE,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

COMMENT ON TABLE  notification         IS '通知表';
COMMENT ON COLUMN notification.user_id IS '接收人 → sys_user.id';
COMMENT ON COLUMN notification.title   IS '通知标题';
COMMENT ON COLUMN notification.content IS '通知内容';
COMMENT ON COLUMN notification.type    IS '通知类型：SIGNUP报名结果，DURATION时长审核，SYSTEM系统公告';
COMMENT ON COLUMN notification.is_read IS '是否已读：true已读，false未读';

-- -------------------------------------------------------------
-- 7. 操作日志表
--    说明：日志按只追加设计，不加 deleted / update_time
-- -------------------------------------------------------------
CREATE TABLE operation_log (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(50),
    operation   VARCHAR(200),
    method      VARCHAR(200),
    params      TEXT,
    ip          VARCHAR(50),
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  operation_log           IS '操作日志表';
COMMENT ON COLUMN operation_log.user_id   IS '操作人ID（未登录可为空）';
COMMENT ON COLUMN operation_log.username  IS '操作人用户名（冗余留存，用户改名后仍可追溯）';
COMMENT ON COLUMN operation_log.operation IS '操作描述';
COMMENT ON COLUMN operation_log.method    IS '调用的方法签名';
COMMENT ON COLUMN operation_log.params    IS '请求参数（建议脱敏后再落库）';
COMMENT ON COLUMN operation_log.ip        IS '来源IP';

-- -------------------------------------------------------------
-- 8. 附件表
--    说明：biz_id 是多态外键（指向不同业务表），故不建物理外键
-- -------------------------------------------------------------
CREATE TABLE attachment (
    id          BIGSERIAL    PRIMARY KEY,
    biz_type    VARCHAR(50),
    biz_id      BIGINT,
    file_name   VARCHAR(255),
    file_url    VARCHAR(255),
    file_size   BIGINT,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  attachment           IS '附件表';
COMMENT ON COLUMN attachment.biz_type  IS '业务类型：ACTIVITY活动封面，ORG组织资质，AVATAR用户头像';
COMMENT ON COLUMN attachment.biz_id    IS '业务ID（多态，不加外键约束）';
COMMENT ON COLUMN attachment.file_name IS '原始文件名';
COMMENT ON COLUMN attachment.file_url  IS '访问地址';
COMMENT ON COLUMN attachment.file_size IS '文件大小（字节）';


-- =============================================================
-- 二、志愿组织域（vcp-org）—— 1 张表
-- =============================================================

-- -------------------------------------------------------------
-- 9. 志愿组织信息表
-- -------------------------------------------------------------
CREATE TABLE org_info (
    id              BIGSERIAL    PRIMARY KEY,
    contact_user_id BIGINT,
    org_name        VARCHAR(100) NOT NULL,
    org_type        VARCHAR(50),
    contact_name    VARCHAR(50),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    description     TEXT,
    status          VARCHAR(20)  DEFAULT 'PENDING',
    deleted         SMALLINT     DEFAULT 0,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_org_contact_user FOREIGN KEY (contact_user_id) REFERENCES sys_user (id)
);

COMMENT ON TABLE  org_info                 IS '志愿组织信息表';
COMMENT ON COLUMN org_info.contact_user_id IS '组织管理员账号ID → sys_user.id';
COMMENT ON COLUMN org_info.org_name        IS '组织名称';
COMMENT ON COLUMN org_info.org_type        IS '组织类型：学院组织、社会团体等';
COMMENT ON COLUMN org_info.contact_name    IS '负责人姓名';
COMMENT ON COLUMN org_info.phone           IS '联系电话';
COMMENT ON COLUMN org_info.email           IS '联系邮箱';
COMMENT ON COLUMN org_info.description     IS '组织简介';
COMMENT ON COLUMN org_info.status          IS '审核状态：PENDING待审核，APPROVED已通过，REJECTED已驳回（由学校管理员审核）';
COMMENT ON COLUMN org_info.deleted         IS '逻辑删除：0未删除，1已删除';


-- =============================================================
-- 三、志愿服务活动域（vcp-volunteer）—— 4 张表
-- =============================================================

-- -------------------------------------------------------------
-- 10. 活动分类表
-- -------------------------------------------------------------
CREATE TABLE activity_category (
    id            BIGSERIAL   PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    sort          INT         DEFAULT 0,
    status        SMALLINT    DEFAULT 1,
    deleted       SMALLINT    DEFAULT 0,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  activity_category               IS '活动分类表';
COMMENT ON COLUMN activity_category.category_name IS '分类名称';
COMMENT ON COLUMN activity_category.sort          IS '排序（升序）';
COMMENT ON COLUMN activity_category.status        IS '状态：1启用，0禁用';
COMMENT ON COLUMN activity_category.deleted       IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 11. 志愿活动表
-- -------------------------------------------------------------
CREATE TABLE volunteer_activity (
    id           BIGSERIAL     PRIMARY KEY,
    title        VARCHAR(200)  NOT NULL,
    category_id  BIGINT,
    org_id       BIGINT,
    start_time   TIMESTAMP,
    end_time     TIMESTAMP,
    location     VARCHAR(200),
    max_count    INT           DEFAULT 0,
    signed_count INT           DEFAULT 0,
    duration     NUMERIC(10,1) DEFAULT 0,
    status       VARCHAR(20)   DEFAULT 'DRAFT',
    cover        VARCHAR(255),
    description  TEXT,
    deleted      SMALLINT      DEFAULT 0,
    create_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_category FOREIGN KEY (category_id) REFERENCES activity_category (id),
    CONSTRAINT fk_activity_org      FOREIGN KEY (org_id)      REFERENCES org_info (id)
);

COMMENT ON TABLE  volunteer_activity              IS '志愿活动表';
COMMENT ON COLUMN volunteer_activity.title        IS '活动名称';
COMMENT ON COLUMN volunteer_activity.category_id  IS '活动分类 → activity_category.id';
COMMENT ON COLUMN volunteer_activity.org_id       IS '发布组织 → org_info.id';
COMMENT ON COLUMN volunteer_activity.start_time   IS '开始时间';
COMMENT ON COLUMN volunteer_activity.end_time     IS '结束时间';
COMMENT ON COLUMN volunteer_activity.location     IS '活动地点';
COMMENT ON COLUMN volunteer_activity.max_count    IS '人数上限；0 表示不限';
COMMENT ON COLUMN volunteer_activity.signed_count IS '已报名人数（冗余计数，报名即占名额：未取消未驳回的报名都计入，驳回与取消各释放一个，用于快速判断是否报满）';
COMMENT ON COLUMN volunteer_activity.duration     IS '预计志愿时长（小时）';
COMMENT ON COLUMN volunteer_activity.status       IS '活动状态：DRAFT草稿，PUBLISHED已发布，CLOSED已结束，CANCELED已取消';
COMMENT ON COLUMN volunteer_activity.cover        IS '封面图地址';
COMMENT ON COLUMN volunteer_activity.description  IS '活动详情';
COMMENT ON COLUMN volunteer_activity.deleted      IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 12. 活动报名表
-- -------------------------------------------------------------
CREATE TABLE activity_signup (
    id           BIGSERIAL    PRIMARY KEY,
    activity_id  BIGINT       NOT NULL,
    student_id   BIGINT       NOT NULL,
    signup_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(20)  DEFAULT 'PENDING',
    audit_remark VARCHAR(255),
    audit_user_id BIGINT,
    audit_time   TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_signup_activity   FOREIGN KEY (activity_id)   REFERENCES volunteer_activity (id),
    CONSTRAINT fk_signup_student    FOREIGN KEY (student_id)    REFERENCES student_info (id),
    CONSTRAINT fk_signup_audit_user FOREIGN KEY (audit_user_id) REFERENCES sys_user (id),
    CONSTRAINT uk_activity_student  UNIQUE (activity_id, student_id)
);

COMMENT ON TABLE  activity_signup              IS '活动报名表';
COMMENT ON COLUMN activity_signup.activity_id  IS '活动ID → volunteer_activity.id';
COMMENT ON COLUMN activity_signup.student_id   IS '学生ID → student_info.id（注意：是学生档案ID，不是 sys_user.id）';
COMMENT ON COLUMN activity_signup.signup_time  IS '报名时间';
COMMENT ON COLUMN activity_signup.status       IS '报名状态：PENDING待审核，APPROVED已通过，REJECTED已驳回，CANCELED已取消，COMPLETED已完成';
COMMENT ON COLUMN activity_signup.audit_remark IS '审核意见';
COMMENT ON COLUMN activity_signup.audit_user_id IS '审核人 → sys_user.id（组织管理员）';
COMMENT ON COLUMN activity_signup.audit_time   IS '审核时间';
COMMENT ON COLUMN activity_signup.deleted      IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 13. 签到签退记录表
--     说明：signup_id 唯一 → 一条报名最多一条签到记录
-- -------------------------------------------------------------
CREATE TABLE attendance_record (
    id            BIGSERIAL   PRIMARY KEY,
    signup_id     BIGINT      NOT NULL UNIQUE,
    sign_in_time  TIMESTAMP,
    sign_out_time TIMESTAMP,
    status        VARCHAR(20) DEFAULT 'NOT_SIGNED',
    remark        VARCHAR(255),
    deleted       SMALLINT    DEFAULT 0,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_signup FOREIGN KEY (signup_id) REFERENCES activity_signup (id)
);

COMMENT ON TABLE  attendance_record               IS '签到签退记录表';
COMMENT ON COLUMN attendance_record.signup_id     IS '报名ID → activity_signup.id（唯一，一条报名对应一条签到记录）';
COMMENT ON COLUMN attendance_record.sign_in_time  IS '签到时间';
COMMENT ON COLUMN attendance_record.sign_out_time IS '签退时间';
COMMENT ON COLUMN attendance_record.status        IS '签到状态：NOT_SIGNED未签到，SIGNED_IN已签到，SIGNED_OUT已签退，ABNORMAL异常，ABSENT缺勤';
COMMENT ON COLUMN attendance_record.remark        IS '备注（异常原因等）';
COMMENT ON COLUMN attendance_record.deleted       IS '逻辑删除：0未删除，1已删除';


-- =============================================================
-- 四、时长认证域（vcp-certification）—— 2 张表
-- =============================================================

-- -------------------------------------------------------------
-- 14. 服务时长表
-- -------------------------------------------------------------
CREATE TABLE service_duration (
    id            BIGSERIAL     PRIMARY KEY,
    signup_id     BIGINT        NOT NULL UNIQUE,
    activity_id   BIGINT        NOT NULL,
    student_id    BIGINT        NOT NULL,
    duration      NUMERIC(10,1) DEFAULT 0,
    status        VARCHAR(20)   DEFAULT 'PENDING_SUBMIT',
    submit_time   TIMESTAMP,
    audit_user_id BIGINT,
    audit_time    TIMESTAMP,
    audit_remark  VARCHAR(255),
    deleted       SMALLINT      DEFAULT 0,
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_duration_signup    FOREIGN KEY (signup_id)   REFERENCES activity_signup (id),
    CONSTRAINT fk_duration_activity  FOREIGN KEY (activity_id) REFERENCES volunteer_activity (id),
    CONSTRAINT fk_duration_student   FOREIGN KEY (student_id)  REFERENCES student_info (id),
    CONSTRAINT fk_duration_audit_user FOREIGN KEY (audit_user_id) REFERENCES sys_user (id)
);

COMMENT ON TABLE  service_duration               IS '服务时长表';
COMMENT ON COLUMN service_duration.signup_id     IS '报名ID → activity_signup.id（唯一，一条报名对应一条时长记录）';
COMMENT ON COLUMN service_duration.activity_id   IS '活动ID → volunteer_activity.id（冗余，便于按活动统计）';
COMMENT ON COLUMN service_duration.student_id    IS '学生ID → student_info.id（冗余，便于按学生/学院统计）';
COMMENT ON COLUMN service_duration.duration      IS '实际服务时长（小时）；由组织管理员依据签到签退记录提交';
COMMENT ON COLUMN service_duration.status        IS '时长状态：PENDING_SUBMIT待提交，PENDING_AUDIT待审核，APPROVED已通过，REJECTED已驳回（由学校管理员终审）';
COMMENT ON COLUMN service_duration.submit_time   IS '提交时间';
COMMENT ON COLUMN service_duration.audit_user_id IS '审核人 → sys_user.id（学校管理员）';
COMMENT ON COLUMN service_duration.audit_time    IS '审核时间';
COMMENT ON COLUMN service_duration.audit_remark  IS '审核意见（驳回原因）';
COMMENT ON COLUMN service_duration.deleted       IS '逻辑删除：0未删除，1已删除';

-- -------------------------------------------------------------
-- 15. 时长审核记录表
--     说明：审核流水按只追加设计，不加 deleted / update_time
-- -------------------------------------------------------------
CREATE TABLE duration_audit (
    id          BIGSERIAL   PRIMARY KEY,
    duration_id BIGINT      NOT NULL,
    auditor_id  BIGINT,
    action      VARCHAR(20),
    remark      VARCHAR(255),
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_duration FOREIGN KEY (duration_id) REFERENCES service_duration (id),
    CONSTRAINT fk_audit_auditor  FOREIGN KEY (auditor_id)  REFERENCES sys_user (id)
);

COMMENT ON TABLE  duration_audit             IS '时长审核记录表（每次提交/审核动作留痕）';
COMMENT ON COLUMN duration_audit.duration_id IS '时长记录ID → service_duration.id';
COMMENT ON COLUMN duration_audit.auditor_id  IS '操作人 → sys_user.id';
COMMENT ON COLUMN duration_audit.action      IS '审核动作：SUBMIT提交，APPROVE通过，REJECT驳回（注意与状态值的 APPROVED/REJECTED 不同形）';
COMMENT ON COLUMN duration_audit.remark      IS '备注';


-- =============================================================
-- 五、公益画像域（vcp-portrait）—— 1 张表
-- =============================================================

-- -------------------------------------------------------------
-- 16. 学生公益画像表
-- -------------------------------------------------------------
CREATE TABLE student_profile (
    id                  BIGSERIAL     PRIMARY KEY,
    student_id          BIGINT        NOT NULL UNIQUE,
    total_activities    INT           DEFAULT 0,
    total_duration      NUMERIC(10,1) DEFAULT 0,
    category_preference VARCHAR(255),
    tags                VARCHAR(255),
    portrait_desc       TEXT,
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_student FOREIGN KEY (student_id) REFERENCES student_info (id)
);

COMMENT ON TABLE  student_profile                     IS '学生公益画像表（每个学生一行，由定时/手动任务重算）';
COMMENT ON COLUMN student_profile.student_id          IS '学生ID → student_info.id（唯一）';
COMMENT ON COLUMN student_profile.total_activities    IS '参与活动次数（按已完成 COMPLETED 的报名计，即真的签到参加了，而非仅报名被批准）';
COMMENT ON COLUMN student_profile.total_duration      IS '累计志愿时长（小时）；权威值在 student_info.total_duration，此处为画像快照，见 README 待定项';
COMMENT ON COLUMN student_profile.category_preference IS '偏好活动类型（参与最多的分类名）';
COMMENT ON COLUMN student_profile.tags                IS '公益标签，逗号分隔（如"热心志愿者,校园服务型"）；【判定规则尚未确定，见 README 待定项】';
COMMENT ON COLUMN student_profile.portrait_desc       IS '画像描述文本';


-- =============================================================
-- 六、索引
-- =============================================================
--
-- 设计说明（与最初 AI 生成的脚本相比做了删减与补充，理由见 sql/README.md）：
--   · 删除了被 UNIQUE 约束覆盖的冗余索引：sys_user(username)、sys_role(role_code)、
--     student_info(student_no)
--   · 删除了被 uk_activity_student(activity_id, student_id) 最左前缀覆盖的
--     activity_signup(activity_id)
--   · attendance_record(signup_id) 同理被其 UNIQUE 约束覆盖，无需另建
--   · 补上知识库《全局技术规范》建议的复合索引，以及看板统计所需索引
--
-- 被删除的索引并非"错误"，只是重复占用空间与写入开销；如需与旧脚本保持一致可自行加回。
-- -------------------------------------------------------------

-- 活动：按状态筛选、按开始时间排序（活动列表页）
CREATE INDEX idx_activity_status     ON volunteer_activity (status);
CREATE INDEX idx_activity_start_time ON volunteer_activity (start_time);
-- 活动：组织端"本组织的活动"按状态筛选
CREATE INDEX idx_activity_org_status ON volunteer_activity (org_id, status);
-- 活动：看板"本月新增活动数"与活动数量趋势图
CREATE INDEX idx_activity_create_time ON volunteer_activity (create_time);

-- 报名：学生端"我的报名"按状态筛选（student_id 为最左前缀，同时覆盖仅按 student_id 的查询）
CREATE INDEX idx_signup_student_status ON activity_signup (student_id, status);

-- 时长：学校端"待审核时长"列表
CREATE INDEX idx_duration_status         ON service_duration (status);
-- 时长：学生端"我的时长"按状态筛选
CREATE INDEX idx_duration_student_status ON service_duration (student_id, status);
-- 时长：看板"各学院志愿时长排名"需要按学院分组，先按学生定位
CREATE INDEX idx_duration_activity       ON service_duration (activity_id);

-- 学生：学院排名与学院维度统计按学院分组
CREATE INDEX idx_student_college ON student_info (college);

-- 通知：学生端"我的通知"与未读数
CREATE INDEX idx_notification_user ON notification (user_id);
