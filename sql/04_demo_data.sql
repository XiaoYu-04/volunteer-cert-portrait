-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 4：演示数据（2026-09-27 重写版，可重复执行）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 【本次数据集】
--   · 学院字典 10 条（与 10_base_and_test_accounts.sql 的清单逐字一致）
--   · 学校管理员 10 名（school_admin_01 ~ school_admin_10，全名）
--   · 组织管理员 10 名（org_admin_01 ~ org_admin_10，全名）+ 11 个已审核组织
--   · 学生 1000 名（学号 12 位结构化：入学年份 4 + 学院 2 + 专业 2 + 班内序号 4）
--   · 志愿活动 10 场（7 场已结束 CLOSED + 3 场已发布 PUBLISHED），每场含封面与详情
--   · 报名 / 签到 / 服务时长 / 时长审核流水 / 公益画像（覆盖 8 类标签与 6 档等级）
--
-- 【与其它脚本的关系】
--   前置：01 → 02 → 03（依赖 03 建好的 3 个角色与 3 个测试账号 admin / org_admin / student）
--   后继：10 → 11 → 12（基础数据兜底、活动图片字段、图片元数据），最后用 09 只读验收
--   ⚠️ **本脚本会写 05 / 06 补的列**（org_info.code/college/member_count/founded_at、
--      student_info.gender/grade、volunteer_activity.deadline/contact、
--      service_duration.org_id/activity_type、notification.source/is_top），
--      所以执行顺序调整为「先补列、再灌数据」：
--          01 → 02 → 03 → 05 → 06 → 04 → 10 → 11 → 12 → 09
--      05 / 06 都是纯增量、可重复执行的补列脚本，提前执行不会改坏数据。
--   07_demo_scale.sql 已删除：放大到 1500 人的旧口径不再需要，本脚本直接给 1000 人。
--
-- 【可重复执行】
--   · 账号 / 学生 / 组织 / 活动按自然键（username / student_no / 组织名 / 活动标题+组织）判重；
--   · 报名 / 签到 / 服务时长按各自的唯一约束（activity_id+student_id / signup_id）判重；
--   · duration_audit 无唯一约束，按 (duration_id, action) 判重；
--   · 汇总回填（signed_count / total_duration / 等级 / 画像）是**幂等重算**，重跑结果完全一致。
--
-- 【确定性】
--   全程不使用 random()：分布一律由「取模 + 步长」决定，任何人执行都得到同一份库。
--   1000 个姓名是脚本内的固定数组（500 男 + 500 女，按奇偶交错取用），学号由规则算出。
--
-- 【口令】
--   所有账号口令均为 123456，库里存 BCrypt 密文（$2a$10$ 开头 60 字符）。
--   同一明文每次哈希结果不同（盐随机），这里固定成同一串只是为了让各人的库便于比对排查。
-- =============================================================

SET client_encoding = 'UTF8';

-- 整脚本包一个事务：中途失败整批回滚，不会留下「学生灌了一半、报名没灌」的库
BEGIN;

-- =============================================================
-- 〇、前置断言
-- =============================================================
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM sys_role
         WHERE deleted = 0 AND role_code IN ('STUDENT', 'ORG_ADMIN', 'SCHOOL_ADMIN')) < 3 THEN
        RAISE EXCEPTION '[04] 缺少 STUDENT / ORG_ADMIN / SCHOOL_ADMIN 角色，请先执行 03_init_data.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'org_admin' AND deleted = 0) THEN
        RAISE EXCEPTION '[04] 缺少测试组织管理员账号 org_admin，请先执行 03_init_data.sql';
    END IF;
    IF (SELECT COUNT(*) FROM activity_category WHERE deleted = 0) < 6 THEN
        RAISE EXCEPTION '[04] 活动分类不足 6 条，请先执行 03_init_data.sql';
    END IF;
END $$;

-- =============================================================
-- 一、学院字典（10 条）
--     与 10_base_and_test_accounts.sql 的清单**必须逐字一致、顺序一致**：
--     学生注册时后端要拿学院名比对字典里的启用项，student_info.college 与
--     org_info.college 存的也是学院名本身，任何一处对不上都会让筛选错位。
-- =============================================================
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort, status) VALUES
('college', '计算机学院',   '计算机学院',   1,  1),
('college', '电子信息学院', '电子信息学院', 2,  1),
('college', '经济管理学院', '经济管理学院', 3,  1),
('college', '外国语学院',   '外国语学院',   4,  1),
('college', '机械工程学院', '机械工程学院', 5,  1),
('college', '化学化工学院', '化学化工学院', 6,  1),
('college', '土木工程学院', '土木工程学院', 7,  1),
('college', '生命科学学院', '生命科学学院', 8,  1),
('college', '文学院',       '文学院',       9,  1),
('college', '医学院',       '医学院',       10, 1)
ON CONFLICT DO NOTHING;

-- =============================================================
-- 二、学校管理员（10 名）
--     用户名 school_admin_01 ~ school_admin_10，real_name 一律是全名。
-- =============================================================
WITH seed(username, real_name, phone, email) AS (
    VALUES
        ('school_admin_01', '陈国华', '13801000001', 'chen.guohua@example.edu.cn'),
        ('school_admin_02', '李慧敏', '13801000002', 'li.huimin@example.edu.cn'),
        ('school_admin_03', '王志远', '13801000003', 'wang.zhiyuan@example.edu.cn'),
        ('school_admin_04', '周文娟', '13801000004', 'zhou.wenjuan@example.edu.cn'),
        ('school_admin_05', '吴建华', '13801000005', 'wu.jianhua@example.edu.cn'),
        ('school_admin_06', '郑晓峰', '13801000006', 'zheng.xiaofeng@example.edu.cn'),
        ('school_admin_07', '孙丽萍', '13801000007', 'sun.liping@example.edu.cn'),
        ('school_admin_08', '黄志强', '13801000008', 'huang.zhiqiang@example.edu.cn'),
        ('school_admin_09', '徐雅琴', '13801000009', 'xu.yaqin@example.edu.cn'),
        ('school_admin_10', '马文博', '13801000010', 'ma.wenbo@example.edu.cn')
)
INSERT INTO sys_user (username, password, real_name, phone, email, status)
SELECT s.username,
       '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa',
       s.real_name, s.phone, s.email, 1
  FROM seed s
 WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = s.username);

WITH seed(username) AS (
    VALUES ('school_admin_01'), ('school_admin_02'), ('school_admin_03'), ('school_admin_04'),
           ('school_admin_05'), ('school_admin_06'), ('school_admin_07'), ('school_admin_08'),
           ('school_admin_09'), ('school_admin_10')
)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
  JOIN sys_role r ON r.role_code = 'SCHOOL_ADMIN' AND r.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- =============================================================
-- 三、组织管理员（10 名）+ 志愿组织（11 个）
--     用户名 org_admin_01 ~ org_admin_10。
--     组织 1（计算机学院青年志愿者协会）由 03_init_data.sql 建好、挂在测试账号
--     org_admin 名下，本脚本不重复建，只补齐 code / college / member_count / founded_at。
--     其余 10 个组织各挂一名组织管理员 —— org_info 上有
--     uk_org_info_contact_user 唯一索引（05 建的），一个账号只能挂一个组织。
-- =============================================================
WITH seed(username, real_name, phone, email) AS (
    VALUES
        ('org_admin_01', '钱静怡', '13802000001', 'qian.jingyi@example.edu.cn'),
        ('org_admin_02', '孙浩然', '13802000002', 'sun.haoran@example.edu.cn'),
        ('org_admin_03', '李思远', '13802000003', 'li.siyuan@example.edu.cn'),
        ('org_admin_04', '周慧敏', '13802000004', 'zhou.huimin@example.edu.cn'),
        ('org_admin_05', '吴天成', '13802000005', 'wu.tiancheng@example.edu.cn'),
        ('org_admin_06', '郑雨欣', '13802000006', 'zheng.yuxin@example.edu.cn'),
        ('org_admin_07', '王嘉豪', '13802000007', 'wang.jiahao@example.edu.cn'),
        ('org_admin_08', '冯雪梅', '13802000008', 'feng.xuemei@example.edu.cn'),
        ('org_admin_09', '韩立新', '13802000009', 'han.lixin@example.edu.cn'),
        ('org_admin_10', '曹文轩', '13802000010', 'cao.wenxuan@example.edu.cn')
)
INSERT INTO sys_user (username, password, real_name, phone, email, status)
SELECT s.username,
       '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa',
       s.real_name, s.phone, s.email, 1
  FROM seed s
 WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = s.username);

WITH seed(username) AS (
    VALUES ('org_admin_01'), ('org_admin_02'), ('org_admin_03'), ('org_admin_04'), ('org_admin_05'),
           ('org_admin_06'), ('org_admin_07'), ('org_admin_08'), ('org_admin_09'), ('org_admin_10')
)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
  JOIN sys_role r ON r.role_code = 'ORG_ADMIN' AND r.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- 10 个新组织（组织 1 由 03 建，见上面的说明）
WITH seed(username, org_name, org_type, college, member_count, founded_at, description) AS (
    VALUES
        ('org_admin_01', '电子信息学院志愿服务队',   '学院组织', '电子信息学院', 126, DATE '2016-09-01',
         '挂靠电子信息学院，长期开展家电维修、科普讲解与社区便民服务。'),
        ('org_admin_02', '经济管理学院公益实践社',   '学院组织', '经济管理学院',  98, DATE '2017-03-15',
         '以公益实践与调研为主，组织环保、助农与社区经济服务类活动。'),
        ('org_admin_03', '外国语学院志愿服务队',     '学院组织', '外国语学院',    84, DATE '2015-10-20',
         '承担大型赛会语言服务与赛事保障，兼顾社区儿童外语启蒙。'),
        ('org_admin_04', '机械工程学院青年志愿者协会', '学院组织', '机械工程学院', 142, DATE '2014-05-04',
         '以社区便民维修、儿童课业辅导与校园服务为主要方向。'),
        ('org_admin_05', '化学化工学院环保志愿服务队', '学院组织', '化学化工学院',  76, DATE '2018-04-22',
         '专注环保宣传、河道清洁与实验安全科普，每年开展环境主题宣传周。'),
        ('org_admin_06', '土木工程学院志愿服务队',   '学院组织', '土木工程学院',  88, DATE '2016-11-11',
         '以敬老助老、冬季送温暖与建筑安全科普为特色。'),
        ('org_admin_07', '生命科学学院科普志愿服务队', '学院组织', '生命科学学院',  69, DATE '2019-09-10',
         '面向中小学生开展生物科普、校园植物导览与标本制作体验。'),
        ('org_admin_08', '文学院文化传播志愿服务队', '学院组织', '文学院',        92, DATE '2015-06-18',
         '以非遗文化进校园、社区阅读推广与旧物循环市集为主。'),
        ('org_admin_09', '医学院健康志愿服务队',     '学院组织', '医学院',       115, DATE '2013-09-01',
         '提供健康宣讲、赛事医疗保障与社区义诊协助服务。'),
        ('org_admin_10', '大学生急救志愿服务队',     '社会团体', '医学院',        64, DATE '2020-05-12',
         '由持有急救证书的学生组成，承担校内外赛事与大型活动的应急保障。')
)
INSERT INTO org_info (contact_user_id, org_name, org_type, contact_name, phone, email,
                      description, status, code, college, member_count, founded_at)
SELECT u.id, s.org_name, s.org_type, u.real_name, u.phone, u.email,
       s.description, 'APPROVED',
       'VCP-ORG-' || lpad((row_number() OVER (ORDER BY s.username))::text, 3, '0'),
       s.college, s.member_count, s.founded_at
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM org_info o WHERE o.contact_user_id = u.id AND o.deleted = 0);

-- 组织 1（03 建的）补齐档案字段，并把负责人改成真实姓名
UPDATE org_info o
   SET contact_name = u.real_name,
       college      = COALESCE(NULLIF(btrim(o.college), ''), '计算机学院'),
       code         = COALESCE(NULLIF(btrim(o.code), ''), 'VCP-ORG-000'),
       member_count = CASE WHEN COALESCE(o.member_count, 0) = 0 THEN 156 ELSE o.member_count END,
       founded_at   = COALESCE(o.founded_at, DATE '2012-03-05'),
       update_time  = CURRENT_TIMESTAMP
  FROM sys_user u
 WHERE o.contact_user_id = u.id
   AND u.username = 'org_admin'
   AND o.deleted = 0;

-- =============================================================
-- 四、1000 名学生
--     学号 = 入学年份(4) + 学院码(2) + 专业码(2) + 班内序号(4)，共 12 位，例如
--     202301010001 = 2023 级 / 计算机学院(01) / 软件工程(01) / 1 号。
--     分布：10 学院 × 2 专业 × 2 年级 × 25 人 = 1000，班级 40 个（每班 25 人）。
--     用户名就是学号（登录支持用户名或学号，两者一致最省事），口令仍是 123456。
--     姓名取脚本内固定数组：奇数序号取女生名、偶数序号取男生名，性别与姓名一致。
-- =============================================================
DROP TABLE IF EXISTS tmp_stu_seed;
CREATE TEMP TABLE tmp_stu_seed AS
WITH male_names AS (SELECT ARRAY[
        '王建国', '曾海峰', '康大伟', '严锦标', '梁海鹏', '田家豪', '武星宇', '刘少辉',
        '苏逸帆', '秦书成', '芦振宇', '谢明远', '范瑞泽', '赖亮明', '赵嘉豪', '蔡启航',
        '顾定国', '章承希', '冯星泽', '姚立诚', '庞志斌', '吴皓天', '魏明诚', '孟俊杰',
        '欧阳承宇', '萧家伟', '邹昱翔', '殷启帆', '胡睿哲', '阎志豪', '段昊然', '诸葛志行',
        '袁子轩', '陆一然', '洪天豪', '林亦轩', '杜承远', '汤晨阳', '尉迟永康', '傅昱辰',
        '白少康', '颜浩宇', '马一诺', '钟子翔', '易沐轩', '李思进', '彭博涛', '毛建波',
        '牛敬轩', '宋文谦', '任明轩', '乔逸辰', '陈子恒', '卢梓晨', '江思齐', '季文昊',
        '韩建军', '方卫华', '龚子睿', '黄国庆', '贾思睿', '侯晓明', '鲁梓轩', '于致远',
        '谭泽民', '樊学军', '徐利伟', '薛嘉言', '龙志刚', '上官云帆', '程晓东', '熊铭泽',
        '施立言', '朱天磊', '余书航', '漕宏达', '皇甫俊楠', '邓建华', '郝海洋', '翟大为',
        '何锦轩', '戴江涛', '尹家铭', '长孙星辰', '沈宏斌', '崔逸杰', '倪学思', '罗振海',
        '汪明宇', '常瑞霖', '张海龙', '吕嘉铭', '邱启帆', '温少华', '郑逸然', '姜星宇',
        '贺立恒', '杨志鑫', '蒋皓晨', '史明信', '俞俊豪', '唐承杰', '石家乐', '文昱豪',
        '周启宸', '丁睿渊', '邵志伟', '葛昊轩', '董志学', '廖子豪', '兰承轩', '孙家俊',
        '叶亦豪', '万承志', '司马朝阳', '曹永杰', '金昱轩', '陶少卿', '高宇航', '潘一航',
        '钱天翔', '慕容亦辰', '许逸群', '孔博远', '安永强', '郭敬尧', '夏文彦', '黎皓宇',
        '王一鸣', '曾子安', '康沐辰', '严思行', '梁文杰', '田建平', '武文斌', '刘子言',
        '苏明辉', '秦逸凡', '芦晓阳', '谢梓涵', '范致诚', '赖文博', '赵学明', '蔡卫东',
        '顾子默', '章国强', '冯思远', '姚晓宇', '庞铭杰', '吴立行', '魏泽宇', '孟书翰',
        '欧阳利军', '萧嘉树', '邹志强', '殷云飞', '胡小龙', '阎锦涛', '段江帆', '诸葛天宇',
        '袁书豪', '陆宏伟', '洪俊宇', '林学智', '杜海涛', '汤明磊', '尉迟锦程', '傅海涛',
        '白嘉伟', '颜星河', '马少杰', '钟逸豪', '易书言', '李振华', '彭明哲', '毛瑞轩',
        '牛亮亮', '宋俊熙', '任启明', '乔定坤', '陈昱杰', '卢星野', '江立新', '季志鹏',
        '韩皓轩', '方明达', '龚子墨', '黄承泽', '贾家辉', '侯亦杰', '鲁启元', '于睿轩',
        '谭志辉', '樊昊宇', '徐志文', '薛宇轩', '龙一帆', '上官天奇', '程亦然', '熊逸伦',
        '施晨曦', '朱永刚', '余敬泽', '漕文卓', '皇甫浩然', '邓一凡', '郝子扬', '翟沐阳',
        '何思远', '戴博文', '尹建伟', '长孙文韬', '沈文彬', '崔明亮', '倪逸轩', '罗子健',
        '汪梓睿', '常致和', '张文轩', '吕学文', '邱卫国', '温子谦', '郑国华', '姜思聪',
        '贺晓辉', '杨梓豪', '蒋立轩', '史泽楷', '俞书睿', '唐利民', '石嘉禾', '文志远',
        '周云鹏', '丁晓峰', '邵铭轩', '葛江山', '董天佑', '廖书杰', '兰宏博', '孙俊驰',
        '叶建国', '万海峰', '司马大伟', '曹锦标', '金海鹏', '陶家豪', '高星宇', '潘少辉',
        '钱逸帆', '慕容书成', '许振宇', '孔明远', '安瑞泽', '郭亮明', '夏嘉豪', '黎启航',
        '王定国', '曾承希', '康星泽', '严立诚', '梁志斌', '田皓天', '武明诚', '刘俊杰',
        '苏承宇', '秦家伟', '芦昱翔', '谢启帆', '范睿哲', '赖志豪', '赵昊然', '蔡志行',
        '顾子轩', '章一然', '冯天豪', '姚亦轩', '庞承远', '吴晨阳', '魏永康', '孟昱辰',
        '欧阳少康', '萧浩宇', '邹一诺', '殷子翔', '胡沐轩', '阎思进', '段博涛', '诸葛建波',
        '袁敬轩', '陆文谦', '洪明轩', '林逸辰', '杜子恒', '汤梓晨', '尉迟思齐', '傅文昊',
        '白建军', '颜卫华', '马子睿', '钟国庆', '易思睿', '李晓明', '彭梓轩', '毛致远',
        '牛泽民', '宋学军', '任利伟', '乔嘉言', '陈志刚', '卢云帆', '江晓东', '季铭泽',
        '韩立言', '方天磊', '龚书航', '黄宏达', '贾俊楠', '侯建华', '鲁海洋', '于大为',
        '谭锦轩', '樊江涛', '徐家铭', '薛星辰', '龙宏斌', '上官逸杰', '程学思', '熊振海',
        '施明宇', '朱瑞霖', '余海龙', '漕嘉铭', '皇甫启帆', '邓少华', '郝逸然', '翟星宇',
        '何立恒', '戴志鑫', '尹皓晨', '长孙明信', '沈俊豪', '崔承杰', '倪家乐', '罗昱豪',
        '汪启宸', '常睿渊', '张志伟', '吕昊轩', '邱志学', '温子豪', '郑承轩', '姜家俊',
        '贺亦豪', '杨承志', '蒋朝阳', '史永杰', '俞昱轩', '唐少卿', '石宇航', '文一航',
        '周天翔', '丁亦辰', '邵逸群', '葛博远', '董永强', '廖敬尧', '兰文彦', '孙皓宇',
        '叶一鸣', '万子安', '司马沐辰', '曹思行', '金文杰', '陶建平', '高文斌', '潘子言',
        '钱明辉', '慕容逸凡', '许晓阳', '孔梓涵', '安致诚', '郭文博', '夏学明', '黎卫东',
        '王子默', '曾国强', '康思远', '严晓宇', '梁铭杰', '田立行', '武泽宇', '刘书翰',
        '苏利军', '秦嘉树', '芦志强', '谢云飞', '范小龙', '赖锦涛', '赵江帆', '蔡天宇',
        '顾书豪', '章宏伟', '冯俊宇', '姚学智', '庞海涛', '吴明磊', '魏锦程', '孟海涛',
        '欧阳嘉伟', '萧星河', '邹少杰', '殷逸豪', '胡书言', '阎振华', '段明哲', '诸葛瑞轩',
        '袁亮亮', '陆俊熙', '洪启明', '林定坤', '杜昱杰', '汤星野', '尉迟立新', '傅志鹏',
        '白皓轩', '颜明达', '马子墨', '钟承泽', '易家辉', '李亦杰', '彭启元', '毛睿轩',
        '牛志辉', '宋昊宇', '任志文', '乔宇轩', '陈一帆', '卢天奇', '江亦然', '季逸伦',
        '韩晨曦', '方永刚', '龚敬泽', '黄文卓', '贾浩然', '侯一凡', '鲁子扬', '于沐阳',
        '谭思远', '樊博文', '徐建伟', '薛文韬', '龙文彬', '上官明亮', '程逸轩', '熊子健',
        '施梓睿', '朱致和', '余文轩', '漕学文', '皇甫卫国', '邓子谦', '郝国华', '翟思聪',
        '何晓辉', '戴梓豪', '尹立轩', '长孙泽楷', '沈书睿', '崔利民', '倪嘉禾', '罗志远',
        '汪云鹏', '常晓峰', '张铭轩', '吕江山', '邱天佑', '温书杰', '郑宏博', '姜俊驰',
        '贺建国', '杨海峰', '蒋大伟', '史锦标', '俞海鹏', '唐家豪', '石星宇', '文少辉',
        '周逸帆', '丁书成', '邵振宇', '葛明远', '董瑞泽', '廖亮明', '兰嘉豪', '孙启航',
        '叶定国', '万承希', '司马星泽', '曹立诚'
    ] AS arr),
     female_names AS (SELECT ARRAY[
        '王丽华', '曾雅萱', '康欣悦', '严可心', '梁梓琪', '田秀华', '武慧琳', '刘思佳',
        '苏若欣', '秦书琪', '芦玉婷', '谢晓雯', '范梦琪', '赖婉琳', '赵沐瑶', '蔡雅婷',
        '顾雨桐', '章诗蕾', '冯语晨', '姚丽芳', '庞静文', '吴欣悦', '魏若彤', '孟梓欣',
        '欧阳桂芳', '萧晓丽', '邹佳敏', '殷依瑶', '胡瑞琳', '阎淑娟', '段晓琴', '诸葛梦雨',
        '袁语嫣', '陆亦欣', '洪雅琪', '林欣妍', '杜可馨', '汤梓萱', '尉迟秀娟', '傅慧英',
        '白思涵', '颜若冰', '马书萱', '钟玉芳', '易晓敏', '李梦瑶', '彭婉君', '毛沐雪',
        '牛雅静', '宋雨萌', '任诗妍', '乔语薇', '陈丽霞', '卢静姝', '江欣瑶', '季若涵',
        '韩梓瑶', '方桂兰', '龚晓燕', '黄佳慧', '贾依萱', '侯瑞欣', '鲁淑英', '于晓霞',
        '谭梦欣', '樊语彤', '徐亦琪', '薛雅丽', '龙欣怡', '上官可妍', '程梓涵', '熊秀梅',
        '施慧芳', '朱思雨', '余若瑶', '漕书瑶', '皇甫玉萍', '邓晓娜', '郝佳欣', '翟婉如',
        '何沐晴', '戴雅琴', '尹雨萱', '长孙诗婷', '沈语晴', '崔丽君', '倪静娴', '罗欣蕊',
        '汪可雯', '常梓莹', '张桂英', '吕慧欣', '邱佳颖', '温依诺', '郑瑞莹', '姜淑芬',
        '贺晓颖', '杨梦莹', '蒋婉柔', '史亦萱', '俞雅君', '唐雨琳', '石可怡', '文嘉宁',
        '周秀珍', '丁慧娟', '邵思敏', '葛若薇', '董心瑶', '廖玉娟', '兰晓芳', '孙佳仪',
        '叶婉清', '万一宁', '司马淑雯', '曹雨薇', '金诗雅', '陶语萱', '高丽娜', '潘静雅',
        '钱欣瑜', '慕容可琳', '许梓雯', '孔秀雯', '安慧兰', '郭佳琪', '夏依晨', '黎瑞琪',
        '王淑华', '曾晓月', '康梦婷', '严婉琪', '梁亦涵', '田雅茹', '武雨琪', '刘可欣',
        '苏嘉莹', '秦秀兰', '芦慧敏', '谢思颖', '范若兰', '赖心蕾', '赵玉华', '蔡晓红',
        '顾佳莹', '章婉婷', '冯一瑶', '姚淑梅', '庞雨欣', '吴诗雯', '魏语欣', '孟丽敏',
        '欧阳静宜', '萧欣彤', '邹可佳', '殷梓晴', '胡秀芳', '阎慧珍', '段佳怡', '诸葛依娜',
        '袁瑞雪', '陆玉琪', '洪晓琳', '林梦雅', '杜婉莹', '汤亦菲', '尉迟雅雯', '傅雨嘉',
        '白诗嘉', '颜嘉雯', '马秀英', '钟静瑶', '易思琪', '李若雪', '彭心语', '毛玉梅',
        '牛晓娟', '宋佳音', '任依静', '乔一菲', '陈淑敏', '卢晓芸', '江诗雨', '季语菲',
        '韩丽娟', '方静雯', '龚欣蕾', '黄可瑶', '贾梓妍', '侯秀敏', '鲁慧雯', '于思嘉',
        '谭依婷', '樊书妍', '徐玉荣', '薛晓晴', '龙梦琳', '上官婉茹', '程沐妍', '熊雅琳',
        '施雨晴', '朱诗悦', '余嘉琪', '漕丽雯', '皇甫静茹', '邓思妍', '郝若曦', '翟心妍',
        '何玉兰', '戴晓梅', '尹佳妮', '长孙依梦', '沈一萱', '崔淑珍', '倪晓静', '罗诗琪',
        '汪语涵', '常丽萍', '张静怡', '吕欣然', '邱可莹', '温梓琳', '郑秀云', '姜慧娜',
        '贺思捷', '杨依琳', '蒋书宁', '史玉洁', '俞晓雪', '唐梦洁', '石婉怡', '文沐琳',
        '周雅芳', '丁雨婷', '邵诗蕊', '葛嘉怡', '董丽颖', '廖静芳', '兰思彤', '孙若琪',
        '叶心怡', '万桂珍', '司马晓萍', '曹佳丽', '金依萍', '陶一诺', '高淑兰', '潘晓慧',
        '钱诗涵', '慕容语琪', '许丽华', '孔雅萱', '安欣悦', '郭可心', '夏梓琪', '黎秀华',
        '王慧琳', '曾思佳', '康若欣', '严书琪', '梁玉婷', '田晓雯', '武梦琪', '刘婉琳',
        '苏沐瑶', '秦雅婷', '芦雨桐', '谢诗蕾', '范语晨', '赖丽芳', '赵静文', '蔡欣悦',
        '顾若彤', '章梓欣', '冯桂芳', '姚晓丽', '庞佳敏', '吴依瑶', '魏瑞琳', '孟淑娟',
        '欧阳晓琴', '萧梦雨', '邹语嫣', '殷亦欣', '胡雅琪', '阎欣妍', '段可馨', '诸葛梓萱',
        '袁秀娟', '陆慧英', '洪思涵', '林若冰', '杜书萱', '汤玉芳', '尉迟晓敏', '傅梦瑶',
        '白婉君', '颜沐雪', '马雅静', '钟雨萌', '易诗妍', '李语薇', '彭丽霞', '毛静姝',
        '牛欣瑶', '宋若涵', '任梓瑶', '乔桂兰', '陈晓燕', '卢佳慧', '江依萱', '季瑞欣',
        '韩淑英', '方晓霞', '龚梦欣', '黄语彤', '贾亦琪', '侯雅丽', '鲁欣怡', '于可妍',
        '谭梓涵', '樊秀梅', '徐慧芳', '薛思雨', '龙若瑶', '上官书瑶', '程玉萍', '熊晓娜',
        '施佳欣', '朱婉如', '余沐晴', '漕雅琴', '皇甫雨萱', '邓诗婷', '郝语晴', '翟丽君',
        '何静娴', '戴欣蕊', '尹可雯', '长孙梓莹', '沈桂英', '崔慧欣', '倪佳颖', '罗依诺',
        '汪瑞莹', '常淑芬', '张晓颖', '吕梦莹', '邱婉柔', '温亦萱', '郑雅君', '姜雨琳',
        '贺可怡', '杨嘉宁', '蒋秀珍', '史慧娟', '俞思敏', '唐若薇', '石心瑶', '文玉娟',
        '周晓芳', '丁佳仪', '邵婉清', '葛一宁', '董淑雯', '廖雨薇', '兰诗雅', '孙语萱',
        '叶丽娜', '万静雅', '司马欣瑜', '曹可琳', '金梓雯', '陶秀雯', '高慧兰', '潘佳琪',
        '钱依晨', '慕容瑞琪', '许淑华', '孔晓月', '安梦婷', '郭婉琪', '夏亦涵', '黎雅茹',
        '王雨琪', '曾可欣', '康嘉莹', '严秀兰', '梁慧敏', '田思颖', '武若兰', '刘心蕾',
        '苏玉华', '秦晓红', '芦佳莹', '谢婉婷', '范一瑶', '赖淑梅', '赵雨欣', '蔡诗雯',
        '顾语欣', '章丽敏', '冯静宜', '姚欣彤', '庞可佳', '吴梓晴', '魏秀芳', '孟慧珍',
        '欧阳佳怡', '萧依娜', '邹瑞雪', '殷玉琪', '胡晓琳', '阎梦雅', '段婉莹', '诸葛亦菲',
        '袁雅雯', '陆雨嘉', '洪诗嘉', '林嘉雯', '杜秀英', '汤静瑶', '尉迟思琪', '傅若雪',
        '白心语', '颜玉梅', '马晓娟', '钟佳音', '易依静', '李一菲', '彭淑敏', '毛晓芸',
        '牛诗雨', '宋语菲', '任丽娟', '乔静雯', '陈欣蕾', '卢可瑶', '江梓妍', '季秀敏',
        '韩慧雯', '方思嘉', '龚依婷', '黄书妍', '贾玉荣', '侯晓晴', '鲁梦琳', '于婉茹',
        '谭沐妍', '樊雅琳', '徐雨晴', '薛诗悦', '龙嘉琪', '上官丽雯', '程静茹', '熊思妍',
        '施若曦', '朱心妍', '余玉兰', '漕晓梅', '皇甫佳妮', '邓依梦', '郝一萱', '翟淑珍',
        '何晓静', '戴诗琪', '尹语涵', '长孙丽萍', '沈静怡', '崔欣然', '倪可莹', '罗梓琳',
        '汪秀云', '常慧娜', '张思捷', '吕依琳', '邱书宁', '温玉洁', '郑晓雪', '姜梦洁',
        '贺婉怡', '杨沐琳', '蒋雅芳', '史雨婷', '俞诗蕊', '唐嘉怡', '石丽颖', '文静芳',
        '周思彤', '丁若琪', '邵心怡', '葛桂珍', '董晓萍', '廖佳丽', '兰依萍', '孙一诺',
        '叶淑兰', '万晓慧', '司马诗涵', '曹语琪'
    ] AS arr),
     raw AS (
         SELECT i,
                1 + ((i - 1) / 100)                    AS college_idx,   -- 1..10
                1 + (((i - 1) / 50) % 2)               AS major_idx,     -- 1..2
                CASE WHEN ((i - 1) / 25) % 2 = 0 THEN 2023 ELSE 2024 END AS grade_year,
                1 + ((i - 1) % 25)                     AS serial,        -- 班内序号 1..25
                CASE WHEN i % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END AS gender,
                (i + 1) / 2                            AS name_idx       -- 1..500
           FROM generate_series(1, 1000) AS i
     ),
     named AS (
         SELECT r.*,
                CASE WHEN r.i % 2 = 0 THEN (SELECT arr FROM male_names)[r.name_idx]
                     ELSE (SELECT arr FROM female_names)[r.name_idx] END AS real_name,
                (ARRAY['计算机学院', '电子信息学院', '经济管理学院', '外国语学院', '机械工程学院',
                       '化学化工学院', '土木工程学院', '生命科学学院', '文学院', '医学院'])[r.college_idx] AS college,
                (ARRAY['软件工程', '计算机科学与技术', '电子信息工程', '通信工程', '工商管理', '会计学',
                       '英语', '日语', '机械设计制造及其自动化', '车辆工程', '化学工程与工艺', '应用化学',
                       '土木工程', '工程管理', '生物技术', '生物科学', '汉语言文学', '新闻学',
                       '临床医学', '护理学'])[(r.college_idx - 1) * 2 + r.major_idx] AS major,
                (ARRAY['软件', '计科', '电信', '通信', '工商', '会计', '英语', '日语', '机制', '车辆',
                       '化工', '应化', '土木', '工管', '生技', '生科', '汉文', '新闻', '临床', '护理'])
                    [(r.college_idx - 1) * 2 + r.major_idx] AS major_short
           FROM raw r
     )
SELECT n.i,
       lpad(n.grade_year::text, 4, '0')
           || lpad(n.college_idx::text, 2, '0')
           || lpad(n.major_idx::text, 2, '0')
           || lpad(n.serial::text, 4, '0')                      AS student_no,
       n.real_name,
       n.gender,
       n.college,
       n.major,
       n.major_short || right(n.grade_year::text, 2) || '01'    AS class_name,
       n.grade_year::text                                       AS grade,
       '1' || (ARRAY['38', '39', '52', '57', '86', '88', '33'])[1 + (n.i % 7)]
             || lpad((((n.i * 7919) % 90000000) + 10000000)::text, 8, '0') AS phone
  FROM named n;

-- 4.1 学生账号
INSERT INTO sys_user (username, password, real_name, phone, email, status)
SELECT s.student_no,
       '$2a$10$jPEdxZ8vkTShM79ugE6IZOPtQaMjGq9QqBFhGc9IpzdhIUVywxEwa',
       s.real_name,
       s.phone,
       s.student_no || '@stu.example.edu.cn',
       1
  FROM tmp_stu_seed s
 WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = s.student_no);

-- 4.2 角色绑定
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
  FROM tmp_stu_seed s
  JOIN sys_user u ON u.username = s.student_no AND u.deleted = 0
  JOIN sys_role r ON r.role_code = 'STUDENT' AND r.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- 4.3 学生档案（total_duration 与等级在第九节统一重算）
INSERT INTO student_info (user_id, student_no, college, major, class_name,
                          total_duration, public_welfare_level, gender, grade)
SELECT u.id, s.student_no, s.college, s.major, s.class_name,
       0, '普通志愿者', s.gender, s.grade
  FROM tmp_stu_seed s
  JOIN sys_user u ON u.username = s.student_no AND u.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM student_info si WHERE si.student_no = s.student_no);

-- 4.4 全量学生清单（含 03 建的测试学生 student，其 student_info.id = 1，排在第一位）
--     s_idx 是后续所有「按人取模」分布的键：1 = 测试学生，2..1001 = 生成的 1000 人。
DROP TABLE IF EXISTS tmp_stu;
CREATE TEMP TABLE tmp_stu AS
SELECT row_number() OVER (ORDER BY si.id) AS s_idx,
       si.id      AS student_id,
       si.student_no,
       si.college
  FROM student_info si
 WHERE si.deleted = 0;

-- 4.5 测试学生补齐 06 补的两列（03 执行时还没有 gender / grade 列）
--     ⚠️ 不能写成 COALESCE(si.gender, 'FEMALE')：06 的回填语句会把当时库里
--     已有的行（只有这一条）按 (id - 1) % 2 填成 MALE，而 06 在 04 之前执行。
--     这里直接按姓名对应的性别写死，避免「林书瑶」显示成男性。
UPDATE student_info si
   SET gender = 'FEMALE',
       grade  = '2022',
       update_time = CURRENT_TIMESTAMP
  FROM sys_user u
 WHERE si.user_id = u.id
   AND u.username = 'student'
   AND si.deleted = 0;

-- 学校管理员清单（时长终审人取模用）
DROP TABLE IF EXISTS tmp_school_admin;
CREATE TEMP TABLE tmp_school_admin AS
SELECT row_number() OVER (ORDER BY u.username) AS idx, u.id AS user_id, u.real_name
  FROM sys_user u
  JOIN sys_user_role ur ON ur.user_id = u.id
  JOIN sys_role r ON r.id = ur.role_id AND r.role_code = 'SCHOOL_ADMIN'
 WHERE u.deleted = 0 AND r.deleted = 0;

-- =============================================================
-- 五、10 场志愿活动
--     1~7 已结束（CLOSED，过去 45~6 天）→ 产生报名、签到、时长与画像
--     8~10 已发布（PUBLISHED，未来 3/8/14 天）→ 产生待审核与已通过的报名
--     每场活动的组织、分类、封面、联系人都在下面的 seed 里写死，便于核对。
--     封面图与 3 张内部图由 12_activity_images_demo.sql 写元数据，
--     图片文件在 volunteer-cert-portrait-server/uploads/demo/activities/。
-- =============================================================
DROP TABLE IF EXISTS tmp_act;
CREATE TEMP TABLE tmp_act AS
WITH seed(idx, slug, title, category_name, org_name, offset_days, duration_h,
          location, max_count, status, description) AS (
    VALUES
        (1, 'library-book-sorting', '校园图书馆图书整理与导读服务', '校园服务', '计算机学院青年志愿者协会',
         -45, 5.0, '学校图书馆一至三层', 400, 'CLOSED',
         '协助图书馆老师整理归还图书、按索书号上架归位，并在服务台为同学提供借还引导与检索帮助。服务前有 20 分钟岗前培训，适合细心、有耐心的同学。'),
        (2, 'elder-apartment-companionship', '社区老年公寓陪伴与文娱服务', '助老服务', '电子信息学院志愿服务队',
         -38, 6.0, '幸福里社区老年公寓', 400, 'CLOSED',
         '陪伴老年公寓的老人聊天、散步，协助开展象棋、手工、合唱等文娱活动，并帮助整理活动室与老人房间。要求有爱心、善于倾听，能用普通话或本地方言交流。'),
        (3, 'river-cleanup-survey', '校园河道垃圾清理与水质记录', '环保公益', '经济管理学院公益实践社',
         -30, 5.0, '校园东侧河道沿线', 400, 'CLOSED',
         '沿校园河道清理塑料瓶、纸屑等垃圾并分类装袋，同时用采样瓶取水、记录水质观察数据。请穿运动鞋，现场统一提供手套、夹子与垃圾袋。'),
        (4, 'marathon-support', '城市马拉松赛事补给与引导', '大型赛事', '外国语学院志愿服务队',
         -24, 8.0, '城市滨江跑道 5 号补给站', 400, 'CLOSED',
         '在马拉松赛道补给站负责倒水、摆放纸杯与能量补给，为选手提供路线引导，并在终点区协助完赛选手领取保温毯。清晨 6:30 集合，服务时间较长，请量力报名。'),
        (5, 'children-afterclass-tutoring', '社区儿童课后陪伴与作业辅导', '社区服务', '机械工程学院青年志愿者协会',
         -18, 5.0, '阳光社区图书室', 400, 'CLOSED',
         '为社区双职工家庭的小学生提供课后作业辅导，陪伴阅读绘本、开展折纸与绘画等兴趣活动。报名后需参加一次 30 分钟线上培训，了解与儿童沟通的注意事项。'),
        (6, 'heritage-culture-campus', '非遗文化进校园宣传与展台讲解', '文化传播', '化学化工学院环保志愿服务队',
         -12, 6.0, '学校文化广场', 400, 'CLOSED',
         '协助非遗文化展台布置与撤展，向同学讲解剪纸、皮影、书法等传统技艺，并引导大家参与体验环节。有讲解经验或书法、手工特长的同学优先。'),
        (7, 'nursing-home-winter-warmth', '敬老院冬季送温暖与健康宣讲', '助老服务', '土木工程学院志愿服务队',
         -6, 6.0, '夕阳红敬老院', 400, 'CLOSED',
         '为敬老院老人送去围巾、手套等冬季用品，陪伴老人晒太阳聊天，并用模型演示七步洗手法、宣讲冬季防跌倒与保暖常识。请提前了解基本老年健康知识。'),
        (8, 'campus-freshman-guide', '校园迎新引导与行李搬运', '校园服务', '生命科学学院科普志愿服务队',
         3, 6.0, '学校南门广场与各宿舍楼', 400, 'PUBLISHED',
         '在迎新服务点为新生和家长提供报到咨询、路线引导，协助搬运行李并介绍校园生活注意事项。服务分上午、下午两班，报名时请在备注里写明可服务的时段。'),
        (9, 'community-recycling-market', '社区旧物循环与环保市集', '环保公益', '文学院文化传播志愿服务队',
         8, 4.0, '阳光社区中心广场', 400, 'PUBLISHED',
         '协助社区旧物交换市集的摊位布置与秩序维护，对捐赠的旧衣物、书籍分类整理，并带小朋友玩垃圾分类小游戏。活动结束后需协助场地清理与物资清点。'),
        (10, 'basketball-league-support', '高校篮球联赛赛事保障', '大型赛事', '医学院健康志愿服务队',
         14, 5.0, '学校体育馆', 400, 'PUBLISHED',
         '为高校篮球联赛提供赛事保障：记录台记分与计时、替补席递水递毛巾、场边应急处理擦伤与扭伤。有急救证或护理专业背景的同学优先，赛前安排一次集中培训。')
)
SELECT s.*, o.id AS org_id, o.contact_user_id AS org_admin_id, c.id AS category_id
  FROM seed s
  JOIN org_info o ON o.org_name = s.org_name AND o.deleted = 0
  JOIN activity_category c ON c.category_name = s.category_name AND c.deleted = 0;

INSERT INTO volunteer_activity (title, category_id, org_id, start_time, end_time, location,
                                max_count, signed_count, duration, status, cover, description,
                                deadline, contact)
SELECT a.title,
       a.category_id,
       a.org_id,
       CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval,
       CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval + (a.duration_h * INTERVAL '1 hour'),
       a.location,
       a.max_count,
       0,
       a.duration_h,
       a.status,
       NULL,
       a.description,
       CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval - INTERVAL '1 day',
       u.real_name || ' ' || u.phone
  FROM tmp_act a
  JOIN sys_user u ON u.id = a.org_admin_id
 WHERE NOT EXISTS (SELECT 1 FROM volunteer_activity va
                    WHERE va.title = a.title AND va.org_id = a.org_id AND va.deleted = 0);
-- 封面（cover）这里刻意留空：图片本体自 2026-09-27 起存进 attachment.file_data，
-- 封面地址由 12_activity_images_demo.sql 在图片记录建好之后回写为
-- /api/v1/attachments/{id}/content，避免先写一个指向磁盘的失效路径。

-- 5.2 已存在的活动按 seed 刷新可变字段
--     上面的 INSERT 带 NOT EXISTS 守卫，重跑不会重复建活动；但如果 seed 里的
--     时长 / 时间 / 名额被调整过，库里那份不会自动跟上，「重跑 04 回到脚本描述的状态」
--     这条约定就不成立。这里显式刷新一遍（幂等）。
--     ⚠️ 只刷新活动本身的字段：报名 / 签到 / 时长等派生数据按各自的唯一约束判重、
--     不会跟着改时间。改过 seed 的时间或时长后，若要让派生数据也一致，请从 02 起重跑整链。
UPDATE volunteer_activity va
   SET start_time  = CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval,
       end_time    = CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval + (a.duration_h * INTERVAL '1 hour'),
       duration    = a.duration_h,
       max_count   = a.max_count,
       status      = a.status,
       location    = a.location,
       description = a.description,
       deadline    = CURRENT_TIMESTAMP + (a.offset_days || ' days')::interval - INTERVAL '1 day',
       contact     = u.real_name || ' ' || u.phone,
       update_time = CURRENT_TIMESTAMP
  FROM tmp_act a
  JOIN sys_user u ON u.id = a.org_admin_id
 WHERE va.title = a.title
   AND va.org_id = a.org_id
   AND va.deleted = 0;

-- 带上库里真实的活动 id 与分类名，后续几节都用它
DROP TABLE IF EXISTS tmp_act_id;
CREATE TEMP TABLE tmp_act_id AS
SELECT a.idx, va.id AS activity_id, va.title, va.status, va.duration AS duration_h,
       va.start_time, va.end_time, a.org_id, a.org_admin_id, a.category_name, a.slug
  FROM tmp_act a
  JOIN volunteer_activity va ON va.title = a.title AND va.org_id = a.org_id AND va.deleted = 0;

-- =============================================================
-- 六、报名记录
--     选人规则（确定性）：先按 s_idx 把学生分成三档 ——
--       重度（s_idx % 10 < 2，20%）每场 60% 概率参加，中度（< 6，40%）30%，
--       轻度（其余 40%）10%；已发布活动概率减半（活动还没开始，报名人数本就少）。
--     取模用的是 (s_idx*31 + 活动序号*17 + s_idx*活动序号*7) % 100 这种混合式，
--     不是单纯的等差步长：后者会让每个学生「参加几场」几乎固定（例如重度一律 2~5 场），
--     分布过于整齐。实跑结果：0 场 250 人 / 1 场 200 / 2 场 240 / 3 场 120 / 4 场 100 /
--     5 场 50 / 6 场 20 / 7 场 21，「长期坚持型（≥6 场）」41 人。
--     测试学生（s_idx = 1）强制参加全部 7 场已结束活动 + 迎新、篮球联赛两场已发布活动，
--     这样 student 账号一登录就能看到完整的「我的报名 / 我的时长 / 公益画像」。
-- =============================================================

-- 6.1 已结束活动：COMPLETED 为主，少量 REJECTED / CANCELED
WITH picked AS (
    SELECT a.activity_id, a.idx, a.org_admin_id,
           s.student_id, s.s_idx,
           a.start_time - (INTERVAL '1 day' * (5 + (s.s_idx % 6))) AS signup_time,
           CASE WHEN (s.s_idx * 7 + a.idx * 3) % 23 = 0 THEN 'REJECTED'
                WHEN (s.s_idx * 5 + a.idx * 3) % 29 = 0 THEN 'CANCELED'
                ELSE 'COMPLETED' END AS st
      FROM tmp_act_id a
      JOIN tmp_stu s
        ON a.status = 'CLOSED'
       AND (s.s_idx = 1
            OR ((s.s_idx * 31 + a.idx * 17 + s.s_idx * a.idx * 7) % 100)
               < (CASE WHEN s.s_idx % 10 < 2 THEN 60
                       WHEN s.s_idx % 10 < 6 THEN 30
                       ELSE 10 END))
)
INSERT INTO activity_signup (activity_id, student_id, signup_time, status,
                             audit_user_id, audit_time, audit_remark)
SELECT p.activity_id, p.student_id, p.signup_time, p.st,
       CASE WHEN p.st IN ('COMPLETED', 'REJECTED') THEN p.org_admin_id END,
       CASE WHEN p.st IN ('COMPLETED', 'REJECTED')
            THEN LEAST(p.signup_time + INTERVAL '1 day', CURRENT_TIMESTAMP) END,
       CASE WHEN p.st = 'REJECTED' THEN '报名信息不完整或名额已满，未通过审核。'
            WHEN p.st = 'CANCELED' THEN '学生本人取消报名。' END
  FROM picked p
 WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg
                    WHERE sg.activity_id = p.activity_id AND sg.student_id = p.student_id);

-- 6.2 已发布活动：APPROVED / PENDING 为主，少量 REJECTED
WITH picked AS (
    SELECT a.activity_id, a.idx, a.org_admin_id,
           s.student_id, s.s_idx,
           a.start_time - (INTERVAL '1 day' * (4 + (s.s_idx % 5))) AS signup_time,
           CASE WHEN (s.s_idx * 11 + a.idx * 5) % 20 = 0 THEN 'REJECTED'
                WHEN (s.s_idx * 3 + a.idx * 5) % 5 = 0 THEN 'PENDING'
                ELSE 'APPROVED' END AS st
      FROM tmp_act_id a
      JOIN tmp_stu s
        ON a.status = 'PUBLISHED'
       AND ((s.s_idx = 1 AND a.idx IN (8, 10))
            OR ((s.s_idx * 31 + a.idx * 17 + s.s_idx * a.idx * 7) % 100)
               < (CASE WHEN s.s_idx % 10 < 2 THEN 30
                       WHEN s.s_idx % 10 < 6 THEN 15
                       ELSE 5 END))
)
INSERT INTO activity_signup (activity_id, student_id, signup_time, status,
                             audit_user_id, audit_time, audit_remark)
SELECT p.activity_id, p.student_id, p.signup_time, p.st,
       CASE WHEN p.st IN ('APPROVED', 'REJECTED') THEN p.org_admin_id END,
       CASE WHEN p.st IN ('APPROVED', 'REJECTED')
            THEN LEAST(p.signup_time + INTERVAL '1 day', CURRENT_TIMESTAMP) END,
       CASE WHEN p.st = 'REJECTED' THEN '报名信息不完整，未通过审核。' END
  FROM picked p
 WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg
                    WHERE sg.activity_id = p.activity_id AND sg.student_id = p.student_id);

-- =============================================================
-- 七、签到记录（只给 COMPLETED 的报名生成）
--     绝大多数正常签到签退：签到 = 活动开始前 5 分钟，签退 = 签到 + 预计时长，
--     即「签退 - 签到 = 活动预计时长」，与第八节的时长认定口径一致。
--     少量 ABSENT（缺勤，无签到签退时间）与 ABNORMAL（设备故障、人工确认到场）。
-- =============================================================
INSERT INTO attendance_record (signup_id, sign_in_time, sign_out_time, status, remark)
SELECT sg.id,
       CASE WHEN x.kind = 'SIGNED_OUT' THEN a.start_time - INTERVAL '5 minutes' END,
       CASE WHEN x.kind = 'SIGNED_OUT'
            THEN a.start_time - INTERVAL '5 minutes' + (a.duration_h * INTERVAL '1 hour') END,
       x.kind,
       CASE WHEN x.kind = 'ABNORMAL' THEN '签到设备故障，已由带队负责人人工确认到场。'
            WHEN x.kind = 'ABSENT'   THEN '活动开始后 30 分钟仍未签到，记为缺勤。' END
  FROM activity_signup sg
  JOIN tmp_act_id a ON a.activity_id = sg.activity_id
  JOIN tmp_stu s ON s.student_id = sg.student_id
  CROSS JOIN LATERAL (
      SELECT CASE WHEN (s.s_idx * 11 + a.idx * 7) % 19 = 0 THEN 'ABSENT'
                  WHEN (s.s_idx * 13 + a.idx * 5) % 41 = 0 THEN 'ABNORMAL'
                  ELSE 'SIGNED_OUT' END AS kind
  ) x
 WHERE sg.status = 'COMPLETED'
   AND NOT EXISTS (SELECT 1 FROM attendance_record ar WHERE ar.signup_id = sg.id);

-- =============================================================
-- 八、服务时长 + 时长审核流水
--     时长 = (签退 - 签到) / 3600，保留 1 位小数（与 02 的 NUMERIC(10,1) 一致）；
--     ABNORMAL 没有签到签退时间，按活动预计时长认定（A1 口径）。
--     状态分布：APPROVED 为主，少量 PENDING_AUDIT（学校端待办）与 REJECTED。
--     已通过 / 已驳回都要有审核人与审核时间（09 自检第 ⑪ 项），
--     待审核必须没有审核时间（第 ⑫ 项）；所有审核时间都不晚于 now()（第 ⑯ 项）。
-- =============================================================
INSERT INTO service_duration (signup_id, activity_id, student_id, duration, status,
                              submit_time, audit_user_id, audit_time, audit_remark,
                              org_id, activity_type)
SELECT sg.id, sg.activity_id, sg.student_id,
       ROUND(CASE WHEN ar.status = 'ABNORMAL' THEN a.duration_h
                  ELSE EXTRACT(EPOCH FROM (ar.sign_out_time - ar.sign_in_time)) / 3600.0 END, 1),
       x.st,
       a.end_time + INTERVAL '1 day',
       CASE WHEN x.st IN ('APPROVED', 'REJECTED') THEN sa.user_id END,
       CASE WHEN x.st IN ('APPROVED', 'REJECTED')
            THEN LEAST(a.end_time + INTERVAL '2 days', CURRENT_TIMESTAMP) END,
       CASE WHEN x.st = 'REJECTED' THEN '签到签退记录不完整，本次时长暂不认定。' END,
       a.org_id,
       a.category_name
  FROM activity_signup sg
  JOIN tmp_act_id a ON a.activity_id = sg.activity_id
  JOIN attendance_record ar ON ar.signup_id = sg.id
  JOIN tmp_stu s ON s.student_id = sg.student_id
  JOIN tmp_school_admin sa ON sa.idx = 1 + (s.s_idx % 11)
  CROSS JOIN LATERAL (
      SELECT CASE WHEN (s.s_idx * 13 + a.idx * 5) % 17 = 0 THEN 'PENDING_AUDIT'
                  WHEN (s.s_idx * 19 + a.idx * 7) % 31 = 0 THEN 'REJECTED'
                  ELSE 'APPROVED' END AS st
  ) x
 WHERE sg.status = 'COMPLETED'
   AND ar.status <> 'ABSENT'
   AND NOT EXISTS (SELECT 1 FROM service_duration sd WHERE sd.signup_id = sg.id);

-- 8.1 提交流水（组织管理员提交）
INSERT INTO duration_audit (duration_id, auditor_id, action, remark, create_time)
SELECT sd.id, a.org_admin_id, 'SUBMIT', '组织提交服务时长，等待学校管理员终审。', sd.submit_time
  FROM service_duration sd
  JOIN tmp_act_id a ON a.activity_id = sd.activity_id
 WHERE sd.submit_time IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM duration_audit da
                    WHERE da.duration_id = sd.id AND da.action = 'SUBMIT');

-- 8.2 审核流水（学校管理员通过 / 驳回）
INSERT INTO duration_audit (duration_id, auditor_id, action, remark, create_time)
SELECT sd.id, sd.audit_user_id,
       CASE WHEN sd.status = 'APPROVED' THEN 'APPROVE' ELSE 'REJECT' END,
       CASE WHEN sd.status = 'APPROVED' THEN '签到签退完整，时长认定通过。'
            ELSE '签到签退记录不完整，驳回。' END,
       sd.audit_time
  FROM service_duration sd
 WHERE sd.status IN ('APPROVED', 'REJECTED')
   AND sd.audit_time IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM duration_audit da
                    WHERE da.duration_id = sd.id AND da.action IN ('APPROVE', 'REJECT'));

-- =============================================================
-- 九、汇总回填（幂等重算，重跑结果一致）
-- =============================================================

-- 9.1 活动的已报名人数（口径：PENDING / APPROVED / COMPLETED 且未删除）
UPDATE volunteer_activity va
   SET signed_count = (SELECT COUNT(*)
                         FROM activity_signup sg
                        WHERE sg.activity_id = va.id
                          AND sg.deleted = 0
                          AND sg.status IN ('PENDING', 'APPROVED', 'COMPLETED')),
       update_time = CURRENT_TIMESTAMP;

-- 9.2 学生累计时长（权威值：service_duration 中 APPROVED 的合计）
UPDATE student_info si
   SET total_duration = COALESCE((SELECT SUM(sd.duration)
                                    FROM service_duration sd
                                   WHERE sd.student_id = si.id
                                     AND sd.deleted = 0
                                     AND sd.status = 'APPROVED'), 0),
       update_time = CURRENT_TIMESTAMP
 WHERE si.deleted = 0;

-- 9.3 公益等级（6 档演示尺度：<3 普通 / 3-6 一星 / 6-10 二星 / 10-20 三星 / 20-40 四星 / >=40 五星）
UPDATE student_info si
   SET public_welfare_level = CASE
           WHEN COALESCE(si.total_duration, 0) >= 40 THEN '五星志愿者'
           WHEN COALESCE(si.total_duration, 0) >= 20 THEN '四星志愿者'
           WHEN COALESCE(si.total_duration, 0) >= 10 THEN '三星志愿者'
           WHEN COALESCE(si.total_duration, 0) >= 6  THEN '二星志愿者'
           WHEN COALESCE(si.total_duration, 0) >= 3  THEN '一星志愿者'
           ELSE '普通志愿者'
       END,
       update_time = CURRENT_TIMESTAMP
 WHERE si.deleted = 0;

-- 9.4 公益画像（整表重算：先清空，再按报名 / 时长明细重算）
--     标签规则（2026-09-22 已拍板）：
--       热心志愿者  = 已完成活动 >= 3 场
--       长期坚持型  = 已完成活动 >= 6 场
--       6 个类型标签 = 参与最多的 2 个分类对应的标签（按分类名映射）
--     一个学生拿不到 8 类，第 ⑳ 项查的是「全库 8 类标签是否都有学生拿到」。
DELETE FROM student_profile;

WITH done AS (
    SELECT sg.student_id,
           COUNT(DISTINCT sg.activity_id) AS acts
      FROM activity_signup sg
     WHERE sg.deleted = 0 AND sg.status = 'COMPLETED'
     GROUP BY sg.student_id
),
cat_count AS (
    SELECT sg.student_id, a.category_name, COUNT(*) AS n
      FROM activity_signup sg
      JOIN tmp_act_id a ON a.activity_id = sg.activity_id
     WHERE sg.deleted = 0 AND sg.status = 'COMPLETED'
     GROUP BY sg.student_id, a.category_name
),
cat_top AS (
    SELECT student_id,
           string_agg(category_name, ',' ORDER BY n DESC, category_name) AS cats,
           (array_agg(category_name ORDER BY n DESC, category_name))[1]  AS top_cat
      FROM (SELECT student_id, category_name, n,
                   row_number() OVER (PARTITION BY student_id
                                      ORDER BY n DESC, category_name) AS rn
              FROM cat_count) t
     WHERE rn <= 2
     GROUP BY student_id
)
INSERT INTO student_profile (student_id, total_activities, total_duration,
                             category_preference, tags, portrait_desc)
SELECT si.id,
       COALESCE(d.acts, 0),
       COALESCE(si.total_duration, 0),
       ct.top_cat,
       NULLIF(concat_ws(',',
           CASE WHEN COALESCE(d.acts, 0) >= 3 THEN '热心志愿者' END,
           CASE WHEN COALESCE(d.acts, 0) >= 6 THEN '长期坚持型' END,
           CASE WHEN ct.cats LIKE '%校园服务%' THEN '校园服务型' END,
           CASE WHEN ct.cats LIKE '%社区服务%' THEN '社区服务型' END,
           CASE WHEN ct.cats LIKE '%环保公益%' THEN '环保行动型' END,
           CASE WHEN ct.cats LIKE '%大型赛事%' THEN '大型活动型' END,
           CASE WHEN ct.cats LIKE '%助老服务%' THEN '助老服务型' END,
           CASE WHEN ct.cats LIKE '%文化传播%' THEN '文化传播型' END
       ), ''),
       CASE WHEN COALESCE(d.acts, 0) = 0
            THEN '暂无志愿服务记录，期待你的第一次参与。'
            ELSE '累计参与 ' || COALESCE(d.acts, 0) || ' 场志愿活动，累计服务 '
                 || COALESCE(si.total_duration, 0) || ' 小时，'
                 || '偏好' || COALESCE(ct.top_cat, '各类') || '活动。' END
  FROM student_info si
  LEFT JOIN done d ON d.student_id = si.id
  LEFT JOIN cat_top ct ON ct.student_id = si.id
 WHERE si.deleted = 0;

-- =============================================================
-- 十、通知（只给 3 个测试账号各 2 条，覆盖报名结果 / 时长审核 / 系统公告三类）
-- =============================================================
WITH seed(username, title, content, type, is_read, source, is_top) AS (
    VALUES
        ('student',   '服务时长认定通过', '你在「校园图书馆图书整理与导读服务」中的服务时长已通过学校管理员终审，可前往「我的时长」查看。', 'DURATION', FALSE, '系统管理员', FALSE),
        ('student',   '报名审核通过',     '你报名的「校园迎新引导与行李搬运」已通过组织审核，请按时参加并记得签到签退。',                 'SIGNUP',   TRUE,  '系统管理员', FALSE),
        ('org_admin', '有新的报名待审核', '「校园迎新引导与行李搬运」有新的学生报名，请前往「报名审核」处理。',                         'SIGNUP',   FALSE, '系统管理员', FALSE),
        ('org_admin', '服务时长已提交',   '你提交的服务时长已进入学校管理员终审环节，结果会通过通知告知。',                               'DURATION', TRUE,  '系统管理员', FALSE),
        ('admin',     '有服务时长待终审', '当前有待审核的服务时长记录，请前往「时长审核」处理。',                                         'DURATION', FALSE, '系统管理员', TRUE),
        ('admin',     '系统数据已更新',   '演示数据已更新为 10 个学院、1000 名学生、10 场活动的数据集。',                                 'SYSTEM',   TRUE,  '系统管理员', FALSE)
)
INSERT INTO notification (user_id, title, content, type, is_read, source, is_top)
SELECT u.id, s.title, s.content, s.type, s.is_read, s.source, s.is_top
  FROM seed s
  JOIN sys_user u ON u.username = s.username AND u.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM notification n WHERE n.user_id = u.id AND n.title = s.title);

-- =============================================================
-- 十一、修复自增序列
--     本脚本大量显式写入 id 之外的行都是自增插入，序列本身会推进；
--     但 03 / 10 里显式写死的 id 不推进序列，这里统一按 max(id) 兜一次，
--     避免应用的下一条插入撞主键（首当其冲的是学生自助注册）。
-- =============================================================
SELECT setval(pg_get_serial_sequence('sys_user',          'id'), COALESCE((SELECT MAX(id) FROM sys_user),          1));
SELECT setval(pg_get_serial_sequence('sys_user_role',     'id'), COALESCE((SELECT MAX(id) FROM sys_user_role),     1));
SELECT setval(pg_get_serial_sequence('student_info',      'id'), COALESCE((SELECT MAX(id) FROM student_info),      1));
SELECT setval(pg_get_serial_sequence('org_info',          'id'), COALESCE((SELECT MAX(id) FROM org_info),          1));
SELECT setval(pg_get_serial_sequence('volunteer_activity', 'id'), COALESCE((SELECT MAX(id) FROM volunteer_activity), 1));
SELECT setval(pg_get_serial_sequence('activity_signup',   'id'), COALESCE((SELECT MAX(id) FROM activity_signup),   1));
SELECT setval(pg_get_serial_sequence('attendance_record', 'id'), COALESCE((SELECT MAX(id) FROM attendance_record), 1));
SELECT setval(pg_get_serial_sequence('service_duration',  'id'), COALESCE((SELECT MAX(id) FROM service_duration),  1));
SELECT setval(pg_get_serial_sequence('duration_audit',    'id'), COALESCE((SELECT MAX(id) FROM duration_audit),    1));
SELECT setval(pg_get_serial_sequence('notification',      'id'), COALESCE((SELECT MAX(id) FROM notification),      1));
SELECT setval(pg_get_serial_sequence('student_profile',   'id'), COALESCE((SELECT MAX(id) FROM student_profile),   1));

COMMIT;

-- =============================================================
-- 十二、自检（只读，供人工核对；期望值写在各行注释里）
-- =============================================================
SELECT 1 AS ord, '学院字典' AS item, COUNT(*) AS actual, 10 AS expected FROM sys_dict WHERE dict_type = 'college'
UNION ALL SELECT 2, '学校管理员', COUNT(*), 11 FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id
           WHERE r.role_code = 'SCHOOL_ADMIN' AND u.deleted = 0
UNION ALL SELECT 3, '组织管理员', COUNT(*), 11 FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id
           WHERE r.role_code = 'ORG_ADMIN' AND u.deleted = 0
UNION ALL SELECT 4, '学生（含测试学生）', COUNT(*), 1001 FROM student_info WHERE deleted = 0
UNION ALL SELECT 5, '志愿组织', COUNT(*), 11 FROM org_info WHERE deleted = 0
UNION ALL SELECT 6, '志愿活动', COUNT(*), 10 FROM volunteer_activity WHERE deleted = 0
UNION ALL SELECT 7, '报名记录', COUNT(*), 2349 FROM activity_signup WHERE deleted = 0
UNION ALL SELECT 8, '签到记录', COUNT(*), 1814 FROM attendance_record WHERE deleted = 0
UNION ALL SELECT 9, '服务时长', COUNT(*), 1716 FROM service_duration WHERE deleted = 0
UNION ALL SELECT 10, '时长审核流水', COUNT(*), 3334 FROM duration_audit
UNION ALL SELECT 11, '公益画像', COUNT(*), 1001 FROM student_profile
UNION ALL SELECT 12, '学生累计时长合计（小时）', COALESCE(ROUND(SUM(total_duration), 1), 0), 9163.0 FROM student_info WHERE deleted = 0
ORDER BY ord;
