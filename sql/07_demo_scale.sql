-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 7/7：演示数据放大（增量，可选，仅供开发调试与答辩演示）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 为什么有这个脚本
-- -------------------------------------------------------------
-- 接上真实后端做前后端联调时发现：演示数据只有 20 场活动 / 149 条报名 /
-- 156.2 小时，而前端原型（mock）是按 386 场 / 12,480 条报名 / 86,420 小时
-- 画的看板 —— 差约 500 倍，答辩时看板数字小得像是接口写错了（待办 A8）。
-- 同时还有几处一直为空的字段（组织编码/挂靠学院/成立时间/成员人数、
-- 报名理由）与一个「演示账号 student 完全没有参与记录」的问题。
--
-- 本脚本一次解决上述全部问题，且**不重建库**：
--   04_demo_data.sql 的规模参数保持不动（它仍是「干净起步」的基线），
--   本脚本在其之上做增量放大，队友正在跑的后端不受影响。
--
-- 规模（执行后应达到，实测值见末尾自检输出）
-- -------------------------------------------------------------
--   组织 8 个（不变）、活动 386 场、学生约 1,500 人、
--   报名约 1 万条、签到约 7 千条、服务时长约 7 千条、累计时长约 2 万小时
--
-- 前置条件
-- -------------------------------------------------------------
--   必须先执行 01 → 02 → 03 → 04 → 05 → 06。
--   本脚本可**重复执行**：各节都有「还不够才补」的守卫，已达标则跳过。
--
-- ⚠️ 与 04 的两处重要差异（04 的坑，本脚本刻意避开）
-- -------------------------------------------------------------
--   1. **04 没有 BEGIN/COMMIT**，每条语句各自自动提交，中途失败会留下半套数据
--      且重跑会撞唯一约束。本脚本显式包在一个事务里，失败整批回滚。
--   2. **04 的报名排序键 `(si2.id*13 + a.id*29) % 37` 只有 37 个取值**。
--      学生数超过 37 后大量并列，`LIMIT` 取哪几行由执行计划决定 ——
--      三个人在不同机器上会得到**不同的报名数据**，违背「三人结果一致」的前提。
--      本脚本一律用 md5(学生id || 活动id) 或大范围线性式做排序/取模键，
--      这些值只由数据本身决定，与行序、并行计划、work_mem 都无关。
--
-- 确定性：全程只用 md5 / 取模 / 算术，无 random()，无 now() 参与排序键。
--
-- ⚠️ 本脚本仅供开发/演示环境使用，切勿在生产环境执行。
-- =============================================================

BEGIN;

-- =============================================================
-- 〇、执行守卫
--
-- 本脚本包在一个事务里，要么全成、要么全回滚，因此「是否已执行过」是个
-- 可靠的二值状态：活动数达到目标即视为已放大过，后续的数据写入整体跳过。
--
-- 为什么要这个守卫而不是逐条 NOT EXISTS：第九节的通知去重若用
-- 「content LIKE '%' || 活动标题 || '%'」逐行判断，会是
-- 报名数 × 通知数（约 1 万 × 1.5 万）次 LIKE，慢到不可接受。
-- 用一个快照判断一次即可。
-- =============================================================
CREATE TEMP TABLE _run_guard ON COMMIT DROP AS
SELECT (SELECT COUNT(*) FROM volunteer_activity WHERE deleted = 0) < 386 AS should_scale;

-- =============================================================
-- 一、补列：activity_category.remark
--
-- 前端分类管理页有「说明」输入框，保存后提示「保存成功」，但库里没有这一列，
-- CategorySaveDTO 收下即丢弃 —— 用户重填的说明刷新就没了（假成功 + 静默丢数据，
-- 待办 B20-2）。补列后后端 CategorySaveDTO/entity/Mapper XML/toVO 四处已同步接上。
--
-- 写法照 sql/06_backend_gap_fix2.sql 的四段式：ALTER → COMMENT → 回填 → 自检。
-- =============================================================
ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS remark VARCHAR(255);
COMMENT ON COLUMN activity_category.remark IS '分类说明；前端分类管理页的「说明」列与编辑弹窗';

-- 回填 6 个分类的说明（按 code 匹配，不按中文名 —— 见待办 B18 的脆弱点）
UPDATE activity_category c
SET remark = v.remark
FROM (VALUES
        ('CAMPUS',     '校园内的日常服务：迎新、图书馆整理、校园导览、秩序维护等'),
        ('COMMUNITY',  '面向周边社区的服务：便民维修、社区课堂、邻里互助等'),
        ('ENVIRONMENT','环境保护类：河道清理、垃圾分类宣传、绿化养护、植树护绿等'),
        ('EVENT',      '大型赛事与活动的服务保障：马拉松、球类联赛、运动会等'),
        ('ELDERLY',    '面向老年人的服务：敬老院陪伴、老年手机课堂、上门慰问等'),
        ('CULTURE',    '文化传播类：非遗宣传、文化节讲解、博物馆导览、校史宣讲等')
     ) AS v(code, remark)
WHERE c.code = v.code
  AND (c.remark IS NULL OR c.remark = '');

-- =============================================================
-- 二、回填一直为空的字段
-- =============================================================

-- 2.1 组织档案：code / college / founded_at / member_count
--
-- OrgVO 与 OrgServiceImpl.toVO() 早就实现了这四个字段，但 03/04 的 INSERT
-- 从没写过它们，全为 NULL；又因 spring.jackson.default-property-inclusion=non_null，
-- 响应里连键都不存在。后果：
--   · 组织档案页「组织编码 / 挂靠学院 / 成立时间」三格恒空白
--   · 「成员人数」恒显示 0 人
--   · 学校端组织管理的「挂靠学院」筛选恒返回 0 条（WHERE college = ? 永不命中）
--     —— 静默空列表，用户会以为「没有这个学院的组织」
--
-- college 取值必须落在 student_info 实际用到的学院里，否则按学院筛选组织
-- 与按学院筛选学生两处会对不上。
UPDATE org_info
SET code = COALESCE(NULLIF(code, ''), 'ORG' || lpad(id::text, 3, '0')),
    college = COALESCE(NULLIF(college, ''),
                       (ARRAY['计算机学院','电子信息学院','经济管理学院',
                              '外国语学院','机械工程学院'])[1 + ((id - 1) % 5)]),
    -- 成立时间落在 2015-01-01 起 8 年内，按 id 确定性取值
    founded_at = COALESCE(founded_at, DATE '2015-01-01' + (((id * 137) % 2920)::int)),
    member_count = CASE
                     WHEN member_count IS NULL OR member_count = 0
                       THEN 60 + ((id * 37) % 240)
                     ELSE member_count
                   END
WHERE deleted = 0;

-- 2.2 报名理由：按 id 确定性抽样，约 2/3 的报名带理由
--
-- 06 补了 reason 列但明确「故意不回填」，于是组织端审核弹窗的「报名理由」
-- 恒显示「学生未填写」。这里补一批，让该字段在演示时看得见。
UPDATE activity_signup
SET reason = (ARRAY[
        '希望通过志愿服务积累社会实践经验，也为社区出一份力。',
        '一直关注这类公益活动，时间也合适，希望能参与。',
        '同学推荐我来报名，说这个活动组织得很好。',
        '想利用课余时间做点有意义的事，锻炼沟通能力。',
        '此前参加过同类活动，比较熟悉流程，希望继续参与。',
        '专业相关，想在实践中巩固所学并服务他人。'
    ])[1 + (id % 6)]
WHERE reason IS NULL
  AND (id % 3) <> 0;

-- =============================================================
-- 三、追加学生账号与档案
--
-- 目标：学生总数 1,500 人（当前 31 人）。
-- 为什么要这么多：等级阈值 3/6/10/20/40 小时是团队确认过的（方案 A 演示尺度），
-- 它同时硬编码在 04 的 CASE 与 vcp-portrait 的 PublicWelfareLevelEnum 里，
-- 两处必须一致、不宜为放大而改。学生数决定了人均时长：1,500 人配约 2 万小时
-- → 人均约 13 小时，六档等级都能铺满，分布也不至于人人五星。
--
-- 用户名/学号一律避开 04 已占用的区间：
--   04 的账号是 stu0001..stu0030，学号 20230002..20230031（均为 4 位序号）。
--   这里用 6 位序号（stu100001 起、2023100001 起），不会与 04 或
--   用户自行注册的 4 位账号相撞。
-- =============================================================
CREATE TEMP TABLE _p ON COMMIT DROP AS
SELECT 1500::int AS target_students;

CREATE TEMP TABLE _new_students ON COMMIT DROP AS
SELECT g AS seq,
       umax.v + g AS user_id,
       smax.v + g AS student_id,
       -- 学院/专业/班级必须用**同一个下标**。04 的注释记着这条坑：
       -- 三个数组各用不同模数会造出「经济管理学院-电子信息工程技术」这类
       -- 现实中不存在的组合（当时 30 个学生错了 24 个）。
       1 + (g % 5) AS college_idx
FROM generate_series(1,
        GREATEST(0, (SELECT target_students FROM _p) - (SELECT COUNT(*) FROM student_info))
     ) AS g,
     (SELECT COALESCE(MAX(id), 0) AS v FROM sys_user) umax,
     (SELECT COALESCE(MAX(id), 0) AS v FROM student_info) smax,
     (SELECT should_scale FROM _run_guard) rg
WHERE rg.should_scale;

INSERT INTO sys_user (id, username, password, real_name, phone, email, status)
SELECT user_id,
       'stu' || lpad((100000 + seq)::text, 6, '0'),
       '123456',
       -- 姓 + 名，两个数组各 10 个元素，按下标确定性组合
       (ARRAY['张','王','李','赵','刘','陈','杨','黄','周','吴'])[1 + (seq % 10)]
           || (ARRAY['伟','芳','娜','敏','静','强','磊','洋','艳','勇'])[1 + ((seq / 10) % 10)],
       '136' || lpad(seq::text, 8, '0'),
       'stu' || lpad((100000 + seq)::text, 6, '0') || '@example.com',
       1
FROM _new_students;

INSERT INTO sys_user_role (user_id, role_id)
SELECT user_id, 1 FROM _new_students;

INSERT INTO student_info (id, user_id, student_no, college, major, class_name,
                          grade, total_duration, public_welfare_level)
SELECT student_id,
       user_id,
       '2023' || lpad((100000 + seq)::text, 6, '0'),
       -- 学院 / 专业 / 班级 三者同下标（college_idx 已在 _new_students 里算好）
       (ARRAY['计算机学院','电子信息学院','经济管理学院','外国语学院','机械工程学院'])[college_idx],
       (ARRAY['软件技术','电子信息工程技术','工商企业管理','商务英语','机械设计与制造'])[college_idx],
       (ARRAY['软件2301','电信2301','工商2301','商英2301','机制2301'])[college_idx],
       -- grade 列由 06 补，此前 StudentVO 不返回它；这里一并填上，让按年级筛选可用
       (ARRAY['2022','2023','2024'])[1 + (seq % 3)],
       0,
       '普通志愿者'
FROM _new_students;

-- =============================================================
-- 四、追加活动
--
-- 目标：活动总数 386 场（当前 20 场）。
--
-- 日期设计（这几条直接决定看板能不能看）：
--   · start_time 必须铺满**近 12 个月**：analytics 的 trend 窗口是
--     [本月-11 月, 本月]，上界是本月最后一天 —— 下月及以后的活动会被
--     **静默丢弃**（所以 04 那 6 场 10 月的 PUBLISHED 活动不进趋势图）。
--   · create_time 必须**显式赋值并跨月**：04 的 20 场全是 DEFAULT CURRENT_TIMESTAMP，
--     导致 stats 的 activities 基期 = 0、monthNew = 全部活动数，四张卡的环比全是 null。
--   · 本月的活动用 LEAST 兜到「昨天」，避免月初执行时出现「已结束但时间在未来」。
--
-- 状态分布：约 8 成 CLOSED（历史，用来生成时长与看板数据），
-- 其余 PUBLISHED / DRAFT / CANCELED，供报名与状态流转演示。
-- =============================================================
CREATE TEMP TABLE _p2 ON COMMIT DROP AS
SELECT 386::int AS target_activities;

CREATE TEMP TABLE _new_activities ON COMMIT DROP AS
SELECT g AS seq,
       amax.v + g AS activity_id,
       1 + (g % 6) AS cat_idx,
       -- 组织只取 1..6：7、8 是 PENDING 组织（04 里 g<=6 才 APPROVED），
       -- 未通过资质审核的组织不该有活动
       1 + (g % 6) AS org_idx,
       -- 近 12 个月：month_off 0..11 映射到「本月-11 月」..「本月」
       (g % 12) AS month_off,
       1 + ((g * 13) % 20) AS day_off,
       -- 每 12 场里有 1 场排在将来（供报名演示）
       CASE WHEN (g % 12) = 0 THEN 'FUTURE' ELSE 'PAST' END AS slot
FROM generate_series(1,
        GREATEST(0, (SELECT target_activities FROM _p2) - (SELECT COUNT(*) FROM volunteer_activity))
     ) AS g,
     (SELECT COALESCE(MAX(id), 0) AS v FROM volunteer_activity) amax,
     (SELECT should_scale FROM _run_guard) rg
WHERE rg.should_scale;

INSERT INTO volunteer_activity
    (id, title, category_id, org_id, start_time, end_time, location, max_count,
     duration, status, description, create_time, deadline, contact)
SELECT n.activity_id,
       -- 标题：类别主题 + 期次，保证唯一且读起来像真的
       (ARRAY['校园服务','社区服务','环保公益','大型赛事','助老服务','文化传播'])[n.cat_idx]
           || ' · ' ||
       (ARRAY[
           ARRAY['校园迎新','图书馆整理','校园导览','教室清洁','宿舍区服务','校园秩序维护'],
           ARRAY['社区便民','社区课堂','社区清洁','社区调研','邻里互助','社区活动协助'],
           ARRAY['河道清理','垃圾分类宣传','绿化养护','环保骑行宣传','植树护绿','低碳宣传'],
           ARRAY['马拉松保障','球类联赛服务','运动会服务','赛事检录','观众引导','赛事补给'],
           ARRAY['敬老院陪伴','老年手机课堂','社区助老','健康讲座协助','上门慰问','助老出行陪同'],
           ARRAY['非遗宣传','文化节讲解','博物馆导览','读书分享会','校史宣讲','文艺演出协助']
       ])[n.cat_idx][1 + (n.seq % 6)]
           || '（第 ' || n.seq || ' 期）',
       n.cat_idx,
       n.org_idx,
       -- 历史活动：落在本月往前 0..11 个月的某天 9 点；
       -- 将来活动：落在今天之后 3..17 天，供报名演示
       CASE WHEN n.slot = 'PAST'
            THEN LEAST(
                   date_trunc('month', CURRENT_DATE)
                       - ((11 - n.month_off) * INTERVAL '1 month')
                       + ((n.day_off - 1) * INTERVAL '1 day')
                       + INTERVAL '9 hours',
                   -- 兜底：本月内算出来的日期若还没到，压到昨天，避免
                   -- 「已结束的活动却在未来」
                   CURRENT_TIMESTAMP - INTERVAL '1 day')
            ELSE CURRENT_TIMESTAMP + ((3 + (n.seq % 15)) * INTERVAL '1 day')
       END,
       -- 结束时间 = 开始 + 活动时长
       CASE WHEN n.slot = 'PAST'
            THEN LEAST(
                   date_trunc('month', CURRENT_DATE)
                       - ((11 - n.month_off) * INTERVAL '1 month')
                       + ((n.day_off - 1) * INTERVAL '1 day')
                       + INTERVAL '9 hours',
                   CURRENT_TIMESTAMP - INTERVAL '1 day')
            ELSE CURRENT_TIMESTAMP + ((3 + (n.seq % 15)) * INTERVAL '1 day')
       END
           + ((2 + (n.seq % 5)) * INTERVAL '1 hour'),
       (ARRAY['学校图书馆','阳光社区','学校南区绿化带','幸福敬老院','学校田径场',
              '学校大学生活动中心','校园河道沿线','学校中心广场','学校体育馆',
              '学校美术馆','社区服务中心','学校北区'])[1 + (n.seq % 12)],
       /* 名额：46..75（原公式 40 + ((n.seq * 7) % 36)，即 40..75）。
          **必须大于第五节报名数上限（45）**：signed_count 是「报名即占名额」
          口径（PENDING + APPROVED + COMPLETED 全计入）。已发布活动的占位比例
          = 60% APPROVED + 35% PENDING = 95%，45 × 0.95 = 42.75 > 40，会顶破
          旧的名额下限 40，末尾自检第 4 项（signed_count > max_count）会失败并
          整批回滚。取 46 > 45 是硬保证，与状态分布无关。 */
       46 + ((n.seq * 7) % 30),
       -- 时长 2..6 小时
       (2 + (n.seq % 5))::numeric(10, 1),
       CASE
           WHEN n.slot = 'FUTURE' THEN 'PUBLISHED'
           WHEN (n.seq % 37) = 0 THEN 'CANCELED'
           WHEN (n.seq % 29) = 0 THEN 'DRAFT'
           ELSE 'CLOSED'
       END,
       '由演示数据脚本生成的历史活动记录，用于填充看板与画像统计。',
       -- create_time 显式赋值：活动开始前 14~25 天创建，跨月分布，
       -- 这样 monthNew 与 activities 的环比才有意义
       (CASE WHEN n.slot = 'PAST'
             THEN LEAST(date_trunc('month', CURRENT_DATE)
                            - ((11 - n.month_off) * INTERVAL '1 month')
                            + ((n.day_off - 1) * INTERVAL '1 day')
                            + INTERVAL '9 hours',
                        CURRENT_TIMESTAMP - INTERVAL '1 day')
             ELSE CURRENT_TIMESTAMP + ((3 + (n.seq % 15)) * INTERVAL '1 day')
        END) - ((14 + (n.seq % 12)) * INTERVAL '1 day'),
       -- 报名截止 = 活动开始前 2 天
       (CASE WHEN n.slot = 'PAST'
             THEN LEAST(date_trunc('month', CURRENT_DATE)
                            - ((11 - n.month_off) * INTERVAL '1 month')
                            + ((n.day_off - 1) * INTERVAL '1 day')
                            + INTERVAL '9 hours',
                        CURRENT_TIMESTAMP - INTERVAL '1 day')
             ELSE CURRENT_TIMESTAMP + ((3 + (n.seq % 15)) * INTERVAL '1 day')
        END)::date - 2,
       (ARRAY['李明','王芳','张伟','刘洋','陈静','杨磊'])[1 + (n.org_idx - 1)] || ' 1390000000' || n.org_idx
FROM _new_activities n;

-- =============================================================
-- 五、追加报名
--
 -- 每个活动取 16..45 人（约 30 人/场）。选人顺序用 md5(学生id || 活动id)，
-- **不用 04 的 `% 37`** —— 那个值域只有 37，学生一多就大量并列，
-- LIMIT 取哪几行由执行计划决定，三个人跑出来的数据会不一样。
--
-- 报名状态沿用 04 的两套取模规则（按活动状态分），保持语义一致：
--   已结束活动：85% COMPLETED / 10% REJECTED / 5% CANCELED
--   已发布活动：60% APPROVED / 35% PENDING / 5% REJECTED
-- 取模键用 (学生id * 31 + 活动id * 17) % 100：只由数据决定，与行序无关。
-- =============================================================
INSERT INTO activity_signup (activity_id, student_id, signup_time, status,
                            audit_user_id, audit_time, audit_remark, reason)
SELECT a.id,
       s.student_id,
       -- LEAST 兜底：已发布活动的时间在未来，减 2~6 天后可能仍落在未来，
       -- 会生成「报名时间在将来」的记录（04 的同一处没有兜底）
       LEAST(a.start_time - ((2 + (s.student_id % 5)) * INTERVAL '1 day'),
             CURRENT_TIMESTAMP),
       CASE
           WHEN a.status = 'CLOSED' THEN
               CASE WHEN (s.student_id * 31 + a.id * 17) % 100 < 85 THEN 'COMPLETED'
                    WHEN (s.student_id * 31 + a.id * 17) % 100 < 95 THEN 'REJECTED'
                    ELSE 'CANCELED' END
           ELSE
               CASE WHEN (s.student_id * 31 + a.id * 17) % 100 < 60 THEN 'APPROVED'
                    WHEN (s.student_id * 31 + a.id * 17) % 100 < 95 THEN 'PENDING'
                    ELSE 'REJECTED' END
       END,
       -- 审核人：只有「经组织管理员审核过」的报名才有；CANCELED 是学生自行取消、PENDING 待审核，均无
       CASE WHEN a.status = 'CLOSED'     AND (s.student_id * 31 + a.id * 17) % 100 < 95 THEN o.contact_user_id
            WHEN a.status = 'PUBLISHED' AND ((s.student_id * 31 + a.id * 17) % 100 < 60
                                          OR (s.student_id * 31 + a.id * 17) % 100 >= 95) THEN o.contact_user_id
            ELSE NULL END,
       -- 审核时间：用 LEAST 兜底，避免「已发布活动的审核时间在未来」
       CASE WHEN a.status = 'CLOSED'     AND (s.student_id * 31 + a.id * 17) % 100 < 95
                 THEN LEAST(a.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP)
            WHEN a.status = 'PUBLISHED' AND ((s.student_id * 31 + a.id * 17) % 100 < 60
                                          OR (s.student_id * 31 + a.id * 17) % 100 >= 95)
                 THEN LEAST(a.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP)
            ELSE NULL END,
       CASE WHEN a.status = 'CLOSED'     AND (s.student_id * 31 + a.id * 17) % 100 BETWEEN 85 AND 94
                 THEN '该同学已有同类活动记录，名额有限'
            WHEN a.status = 'PUBLISHED' AND (s.student_id * 31 + a.id * 17) % 100 >= 95
                 THEN '报名信息不完整，请补充联系方式'
            ELSE NULL END,
       (ARRAY['希望通过志愿服务积累社会实践经验，也为社区出一份力。',
              '一直关注这类公益活动，时间也合适，希望能参与。',
              '同学推荐我来报名，说这个活动组织得很好。',
              '想利用课余时间做点有意义的事，锻炼沟通能力。',
              '此前参加过同类活动，比较熟悉流程，希望继续参与。',
              '专业相关，想在实践中巩固所学并服务他人。'])[1 + (s.student_id % 6)]
FROM volunteer_activity a
JOIN org_info o ON o.id = a.org_id
-- 只给本脚本新增的活动补报名：04 已为前 20 场生成过，重复补会撞
-- uk_activity_student（唯一约束：同一学生对同一活动只保留一行）
JOIN _new_activities n ON n.activity_id = a.id
CROSS JOIN LATERAL (
    SELECT si.id AS student_id
    FROM student_info si
    WHERE si.deleted = 0
    ORDER BY md5(si.id::text || ':' || a.id::text)
    -- 每场 16..45 人。上限 45 的来历有两条：
    -- ① 保证「五星志愿者（≥40 小时）」一档不为空：原为 8..39 时人均只参与
    --    5.5 场、最高 9 场 ≈ 37 小时，六档等级里五星恒空，末尾自检第 3 项失败；
    -- ② 新口径（报名即占名额）下，名额下限 46 > 报名数上限 45 才是末尾自检
    --    第 4 项（signed_count > max_count）安全的保证，与状态分布无关。
    LIMIT 16 + ((a.id * 7919) % 30)
) s
WHERE a.status IN ('CLOSED', 'PUBLISHED');

-- =============================================================
-- 六、追加签到记录
-- 仅对已完成的报名生成：80% 正常签退 / 10% 异常 / 10% 缺勤。
-- 与 04 同一套规则，但取模键换成 (学生id*7 + 活动id*3) % 10，
-- 不依赖 attendance_record.id（放大后行序不稳）。
-- =============================================================
INSERT INTO attendance_record (signup_id, sign_in_time, sign_out_time, status, remark)
SELECT sg.id,
       CASE WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 9
            THEN a.start_time + INTERVAL '10 minutes' ELSE NULL END,
       CASE WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 8 THEN a.end_time
            WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 9
            THEN a.start_time + INTERVAL '50 minutes'
            ELSE NULL END,
       CASE WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 8 THEN 'SIGNED_OUT'
            WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 9 THEN 'ABNORMAL'
            ELSE 'ABSENT' END,
       CASE WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 8 THEN '正常签到签退'
            WHEN (sg.student_id * 7 + sg.activity_id * 3) % 10 < 9
            THEN '提前离场，签退时间明显早于活动结束时间，已由组织管理员标记'
            ELSE '未到场，记为缺勤' END
FROM activity_signup sg
JOIN volunteer_activity a ON a.id = sg.activity_id
JOIN _new_activities n ON n.activity_id = sg.activity_id
WHERE sg.status = 'COMPLETED';

-- =============================================================
-- 七、追加服务时长记录
-- 仅对已签到（含异常）的报名生成；缺勤不产生时长。75% 通过 / 15% 待审核 / 10% 驳回。
-- duration 沿用 04 的算法：正常签退 = 活动时长 − 10 分钟；异常 = 0.7 小时。
-- =============================================================
INSERT INTO service_duration (signup_id, activity_id, student_id, duration, status,
                              submit_time, audit_user_id, audit_time, audit_remark,
                              org_id, activity_type)
SELECT sg.id,
       sg.activity_id,
       sg.student_id,
       CASE WHEN ar.status = 'SIGNED_OUT'
            THEN ROUND((EXTRACT(EPOCH FROM (ar.sign_out_time - ar.sign_in_time)) / 3600.0)::numeric, 1)
            ELSE 0.7 END,
       CASE WHEN (sg.student_id * 11 + sg.activity_id * 5) % 100 < 75 THEN 'APPROVED'
            WHEN (sg.student_id * 11 + sg.activity_id * 5) % 100 < 90 THEN 'PENDING_AUDIT'
            ELSE 'REJECTED' END,
       a.end_time + INTERVAL '1 day',
       CASE WHEN (sg.student_id * 11 + sg.activity_id * 5) % 100 < 75
             OR (sg.student_id * 11 + sg.activity_id * 5) % 100 >= 90
            THEN (SELECT id FROM sys_user WHERE username = 'admin') ELSE NULL END,
       CASE WHEN (sg.student_id * 11 + sg.activity_id * 5) % 100 < 75
             OR (sg.student_id * 11 + sg.activity_id * 5) % 100 >= 90
            THEN a.end_time + INTERVAL '2 days' ELSE NULL END,
       CASE WHEN (sg.student_id * 11 + sg.activity_id * 5) % 100 >= 90
            THEN '服务时长与签到记录不符，请重新核对后提交' ELSE NULL END,
       a.org_id,
       ac.category_name
FROM activity_signup sg
JOIN volunteer_activity a ON a.id = sg.activity_id
JOIN attendance_record ar ON ar.signup_id = sg.id
JOIN activity_category ac ON ac.id = a.category_id
JOIN _new_activities n ON n.activity_id = sg.activity_id
WHERE sg.status = 'COMPLETED'
  AND ar.status IN ('SIGNED_OUT', 'ABNORMAL');

-- =============================================================
-- 八、追加时长审核流水
-- 每条时长一条 SUBMIT；已通过/已驳回的再补一条 APPROVE/REJECT。
--
-- 注意 duration_audit **只有 create_time，没有 audit_time 列**
-- （建表见 02_schema.sql:428；04 也只插 4 列）。流水时间由 create_time 承担。
-- =============================================================
INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id,
       o.contact_user_id,
       'SUBMIT',
       '活动已结束，提交本活动服务时长'
FROM service_duration sd
JOIN volunteer_activity a ON a.id = sd.activity_id
JOIN org_info o ON o.id = a.org_id
JOIN _new_activities n ON n.activity_id = sd.activity_id
-- 只补本脚本新增的时长：04 已为它那批写过流水
WHERE sd.status IN ('APPROVED', 'PENDING_AUDIT', 'REJECTED')
  AND NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id AND da.action = 'SUBMIT');

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id,
       (SELECT id FROM sys_user WHERE username = 'admin'),
       CASE WHEN sd.status = 'APPROVED' THEN 'APPROVE' ELSE 'REJECT' END,
       sd.audit_remark
FROM service_duration sd
JOIN _new_activities n ON n.activity_id = sd.activity_id
WHERE sd.status IN ('APPROVED', 'REJECTED')
  AND NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id
                    AND da.action IN ('APPROVE', 'REJECT'));

-- =============================================================
-- 九、追加报名与时长通知
-- =============================================================
INSERT INTO notification (user_id, title, content, type, is_read)
SELECT si.user_id,
       CASE WHEN sg.status = 'COMPLETED'
            THEN '活动已完成' ELSE '报名审核通过' END,
       CASE WHEN sg.status = 'COMPLETED'
            THEN '您报名的活动「' || a.title || '」已完成，服务时长待组织管理员提交。'
            ELSE '您报名的活动「' || a.title || '」已通过审核。' END,
       'SIGNUP',
       (sg.student_id % 3 = 0)
FROM activity_signup sg
JOIN student_info si ON si.id = sg.student_id
JOIN volunteer_activity a ON a.id = sg.activity_id
JOIN _new_activities n ON n.activity_id = sg.activity_id
CROSS JOIN _run_guard rg
WHERE rg.should_scale
  AND sg.status IN ('APPROVED', 'COMPLETED');

INSERT INTO notification (user_id, title, content, type, is_read)
SELECT si.user_id,
       CASE WHEN sd.status = 'APPROVED' THEN '服务时长审核通过' ELSE '服务时长审核驳回' END,
       '您参与的「' || a.title || '」服务时长 ' || sd.duration || ' 小时' ||
       CASE WHEN sd.status = 'APPROVED' THEN '已通过审核，已计入累计时长。'
            ELSE '被驳回：' || COALESCE(sd.audit_remark, '请核对后重新提交。') END,
       'DURATION',
       (sd.student_id % 2 = 0)
FROM service_duration sd
JOIN student_info si ON si.id = sd.student_id
JOIN volunteer_activity a ON a.id = sd.activity_id
JOIN _new_activities n ON n.activity_id = sd.activity_id
CROSS JOIN _run_guard rg
WHERE rg.should_scale
  AND sd.status IN ('APPROVED', 'REJECTED');

-- =============================================================
-- 十、演示账号 student（学生档案 id=1）的参与记录
--
-- 这是答辩最常演示的账号，而 04 的报名是用「偏好分类 + LIMIT」选的，
-- 学生 1 一次都没被选中 —— 于是「我的服务时长」是空的、「我的公益画像」
-- 是 0 小时 0 标签的普通志愿者，学生端整条主线看起来像坏了。
-- 这里给它补 6 场已结束活动的完整闭环（报名 → 签到 → 时长 → 已通过）。
--
-- 目标档位：约 14 小时 → 三星志愿者，画像有「热心志愿者 + 长期坚持型 + 类型标签」。
-- =============================================================
CREATE TEMP TABLE _demo_activities ON COMMIT DROP AS
SELECT a.id, a.title, a.start_time, a.end_time, a.duration, a.org_id
FROM volunteer_activity a
WHERE a.status = 'CLOSED'
  -- 排除该学生已有报名的活动：activity_signup 上有 uk_activity_student
  -- （同一学生对同一活动只保留一行），撞上会整批回滚。
  -- 04 给演示学生留了 3 条报名（1 条 REJECTED 在已结束活动上），必须避开。
  AND NOT EXISTS (SELECT 1 FROM activity_signup sg
                  WHERE sg.activity_id = a.id AND sg.student_id = 1)
  -- 与全脚本同一个开关：已放大过就不再补，保证重复执行幂等
  AND (SELECT should_scale FROM _run_guard)
ORDER BY md5('demo-student-1:' || a.id::text)
LIMIT 6;

INSERT INTO activity_signup (activity_id, student_id, signup_time, status,
                            audit_user_id, audit_time, reason)
SELECT d.id, 1,
       d.start_time - INTERVAL '3 days',
       'COMPLETED',
       o.contact_user_id,
       LEAST(d.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP),
       '希望通过志愿服务积累社会实践经验，也为社区出一份力。'
FROM _demo_activities d
JOIN org_info o ON o.id = d.org_id
WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg
                  WHERE sg.activity_id = d.id AND sg.student_id = 1);

INSERT INTO attendance_record (signup_id, sign_in_time, sign_out_time, status, remark)
SELECT sg.id, d.start_time + INTERVAL '10 minutes', d.end_time, 'SIGNED_OUT', '正常签到签退'
FROM activity_signup sg
JOIN _demo_activities d ON d.id = sg.activity_id
WHERE sg.student_id = 1
  AND NOT EXISTS (SELECT 1 FROM attendance_record ar WHERE ar.signup_id = sg.id);

INSERT INTO service_duration (signup_id, activity_id, student_id, duration, status,
                              submit_time, audit_user_id, audit_time, org_id, activity_type)
SELECT sg.id, d.id, 1,
       ROUND((d.duration - (10.0 / 60.0))::numeric, 1),
       'APPROVED',
       d.end_time + INTERVAL '1 day',
       (SELECT id FROM sys_user WHERE username = 'admin'),
       d.end_time + INTERVAL '2 days',
       d.org_id,
       (SELECT ac.category_name FROM activity_category ac
        JOIN volunteer_activity va ON va.category_id = ac.id WHERE va.id = d.id)
FROM activity_signup sg
JOIN _demo_activities d ON d.id = sg.activity_id
WHERE sg.student_id = 1
  AND NOT EXISTS (SELECT 1 FROM service_duration sd WHERE sd.signup_id = sg.id);

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id, o.contact_user_id, 'SUBMIT', '活动已结束，提交本活动服务时长'
FROM service_duration sd
JOIN volunteer_activity a ON a.id = sd.activity_id
JOIN org_info o ON o.id = a.org_id
JOIN _demo_activities d ON d.id = sd.activity_id
WHERE sd.student_id = 1
  AND NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id AND da.action = 'SUBMIT');

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id, (SELECT id FROM sys_user WHERE username = 'admin'), 'APPROVE', NULL
FROM service_duration sd
JOIN _demo_activities d ON d.id = sd.activity_id
WHERE sd.student_id = 1 AND sd.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id AND da.action = 'APPROVE');

-- =============================================================
-- 十之二、把「高活跃学生」定向补进五星档（≥40 小时）
--
-- 为什么必须定向补，而不能靠调大每场报名人数：
--   一次报名要过三层折算才会变成一条有效时长 ——
--     85%（已结束活动的 COMPLETED）× 90%（非缺勤）× 75%（时长审核通过）≈ 57%，
--   每条有效时长约 4 小时。于是人均 7 次报名 ≈ 12.6 小时；
--   而 40 小时需要约 10 条有效时长 ≈ 18 次报名。
--   若把每场报名人数上调到人均 18 次，总报名数会冲到 3 万条
--   （前端原型只有 12,480），得不偿失。
--
-- 所以这里对「已攒到 32..40 小时」的学生定向补 4 场已结束活动，
-- 使其稳定跨过 40 小时。做法与第十节给演示账号补记录完全一致。
--
-- 两条护栏：
--   1) 补的活动必须有剩余名额 —— 已报名数 ≤ 28 才选（28 + 4 = 32 条报名，
--      新口径下这 32 条全部占名额，仍低于名额下限 46，自检第 4 项安全）。
--   2) 避开该学生已有报名的活动 —— activity_signup 上有 uk_activity_student，
--      同一学生对同一活动只能有一行，撞上会整批回滚。
-- =============================================================
CREATE TEMP TABLE _star_boost ON COMMIT DROP AS
SELECT c.student_id, a.id AS activity_id, a.org_id, a.start_time, a.end_time, a.duration
FROM (
    SELECT si.id AS student_id
    FROM student_info si
    CROSS JOIN _run_guard rg
    WHERE rg.should_scale
      AND si.deleted = 0
      AND COALESCE((SELECT SUM(sd.duration) FROM service_duration sd
                    WHERE sd.student_id = si.id AND sd.status = 'APPROVED'), 0)
          >= 32
      AND COALESCE((SELECT SUM(sd.duration) FROM service_duration sd
                    WHERE sd.student_id = si.id AND sd.status = 'APPROVED'), 0)
          < 40
    ORDER BY md5('star-boost:' || si.id::text)
    LIMIT 50
) c
CROSS JOIN LATERAL (
    SELECT va.id, va.org_id, va.start_time, va.end_time, va.duration
    FROM volunteer_activity va
    WHERE va.status = 'CLOSED'
      -- 只挑时长 >= 4 小时的活动：4 场至少 +15 小时，保证 32..40 的人必然跨过 40
      AND va.duration >= 4
      AND (SELECT COUNT(*) FROM activity_signup sg3
           WHERE sg3.activity_id = va.id AND sg3.deleted = 0) <= 28
      AND NOT EXISTS (SELECT 1 FROM activity_signup sg
                      WHERE sg.activity_id = va.id AND sg.student_id = c.student_id)
    ORDER BY md5('star-boost:' || c.student_id::text || ':' || va.id::text)
    LIMIT 4
) a;

INSERT INTO activity_signup (activity_id, student_id, signup_time, status,
                            audit_user_id, audit_time, reason)
SELECT b.activity_id, b.student_id,
       b.start_time - INTERVAL '3 days',
       'COMPLETED',
       o.contact_user_id,
       LEAST(b.start_time - INTERVAL '12 hours', CURRENT_TIMESTAMP),
       '希望通过志愿服务积累社会实践经验，也为社区出一份力。'
FROM _star_boost b
JOIN org_info o ON o.id = b.org_id
WHERE NOT EXISTS (SELECT 1 FROM activity_signup sg
                  WHERE sg.activity_id = b.activity_id AND sg.student_id = b.student_id);

INSERT INTO attendance_record (signup_id, sign_in_time, sign_out_time, status, remark)
SELECT sg.id, b.start_time + INTERVAL '10 minutes', b.end_time, 'SIGNED_OUT', '正常签到签退'
FROM activity_signup sg
JOIN _star_boost b ON b.activity_id = sg.activity_id AND b.student_id = sg.student_id
WHERE NOT EXISTS (SELECT 1 FROM attendance_record ar WHERE ar.signup_id = sg.id);

INSERT INTO service_duration (signup_id, activity_id, student_id, duration, status,
                              submit_time, audit_user_id, audit_time, org_id, activity_type)
SELECT sg.id, b.activity_id, b.student_id,
       ROUND((b.duration - (10.0 / 60.0))::numeric, 1),
       'APPROVED',
       b.end_time + INTERVAL '1 day',
       (SELECT id FROM sys_user WHERE username = 'admin'),
       b.end_time + INTERVAL '2 days',
       b.org_id,
       (SELECT ac.category_name FROM activity_category ac
        JOIN volunteer_activity va ON va.category_id = ac.id WHERE va.id = b.activity_id)
FROM activity_signup sg
JOIN _star_boost b ON b.activity_id = sg.activity_id AND b.student_id = sg.student_id
WHERE NOT EXISTS (SELECT 1 FROM service_duration sd WHERE sd.signup_id = sg.id);

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id, o.contact_user_id, 'SUBMIT', '活动已结束，提交本活动服务时长'
FROM service_duration sd
JOIN volunteer_activity a ON a.id = sd.activity_id
JOIN org_info o ON o.id = a.org_id
JOIN _star_boost b ON b.activity_id = sd.activity_id AND b.student_id = sd.student_id
WHERE NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id AND da.action = 'SUBMIT');

INSERT INTO duration_audit (duration_id, auditor_id, action, remark)
SELECT sd.id, (SELECT id FROM sys_user WHERE username = 'admin'), 'APPROVE', NULL
FROM service_duration sd
JOIN _star_boost b ON b.activity_id = sd.activity_id AND b.student_id = sd.student_id
WHERE sd.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM duration_audit da
                  WHERE da.duration_id = sd.id AND da.action = 'APPROVE');
-- =============================================================
-- 十一、重跑聚合回填
--
-- 这几段与 04 第八、九节口径完全一致（必须一致，否则等级/标签会两套算法打架）。
-- 差别只在「范围」：这里对全表重算，因为新增数据会改变每个学生的汇总值。
-- =============================================================

-- 11.1 学生累计有效时长：只统计审核通过的时长
UPDATE student_info si
SET total_duration = COALESCE((
        SELECT SUM(sd.duration)
        FROM service_duration sd
        WHERE sd.student_id = si.id
          AND sd.status = 'APPROVED'
    ), 0);

-- 11.2 公益等级：阈值与 04 第八节、vcp-portrait 的 PublicWelfareLevelEnum 逐条一致。
--      改这里必须同时改那两处（B10 已验证三者等价）。
UPDATE student_info
SET public_welfare_level = CASE
        WHEN total_duration >= 40 THEN '五星志愿者'
        WHEN total_duration >= 20 THEN '四星志愿者'
        WHEN total_duration >= 10 THEN '三星志愿者'
        WHEN total_duration >= 6  THEN '二星志愿者'
        WHEN total_duration >= 3  THEN '一星志愿者'
        ELSE '普通志愿者'
    END;

-- 11.3 名额下限兜底修正（幂等）：第四节的活动 INSERT 带 should_scale 守卫
--      （WHERE rg.should_scale），库里活动数已达 386 时整段跳过，
--      不会修正老数据，所以要在本节兜一次；本条可重复执行。
--      它会连带把 04_demo_data.sql 那 20 场的名额（15..60）也抬到 46，
--      这是有意的：演示库里名额下限只保留一套，避免同一张表出现两种
--      下限口径（那 20 场报名数 ≤ 12，本来不需要抬）。
UPDATE volunteer_activity SET max_count = 46 WHERE deleted = 0 AND max_count > 0 AND max_count < 46;

-- 11.4 活动已报名人数：报名即占名额 —— 未取消未驳回的报名都计入
--      （与运行期实现、前端 mock 一致）
UPDATE volunteer_activity va
SET signed_count = (
        SELECT COUNT(*)
        FROM activity_signup sg
        WHERE sg.activity_id = va.id
          AND sg.status IN ('PENDING', 'APPROVED', 'COMPLETED')
    );

-- 11.5 学生公益画像（等级/标签规则与 04 第九节一致）
--
-- 与 04 的差别：04 每个学生跑 5 次相关子查询；这里先物化一次「已完成活动数」
-- 与「偏好分类」，再 JOIN 回学生表 —— 1,500 人规模下差别很明显。
-- 用 DELETE + INSERT 而不是 INSERT：画像表没有唯一约束，直接插会产生重复行。
DELETE FROM student_profile;

CREATE TEMP TABLE _stu_agg ON COMMIT DROP AS
SELECT si.id AS student_id,
       COALESCE(cnt.done_count, 0) AS done_count,
       pref.category_name AS pref_category
FROM student_info si
LEFT JOIN (
    SELECT sg.student_id, COUNT(DISTINCT sg.activity_id) AS done_count
    FROM activity_signup sg
    WHERE sg.status = 'COMPLETED'
    GROUP BY sg.student_id
) cnt ON cnt.student_id = si.id
LEFT JOIN LATERAL (
    -- 参与次数最多的分类；并列时按分类 id 升序打破（不用中文名排序：
    -- 中文串的排序结果依赖数据库 collation，会让不同机器结果不一致）
    SELECT ac.category_name
    FROM activity_signup sg
    JOIN volunteer_activity a ON a.id = sg.activity_id
    JOIN activity_category ac ON ac.id = a.category_id
    WHERE sg.student_id = si.id AND sg.status = 'COMPLETED'
    GROUP BY ac.category_name
    ORDER BY COUNT(*) DESC, MIN(ac.id)
    LIMIT 1
) pref ON TRUE
WHERE si.deleted = 0;

INSERT INTO student_profile (student_id, total_activities, total_duration,
                             category_preference, tags, portrait_desc)
SELECT g.student_id,
       g.done_count,
       si.total_duration,
       g.pref_category,
       -- 外层 NULLIF：三个 CASE 全空时 CONCAT_WS 返回空串而非 NULL，
       -- 前端按逗号切分会渲染出一个空标签
       NULLIF(CONCAT_WS(',',
           CASE WHEN si.total_duration > 0 THEN '热心志愿者' END,
           CASE WHEN g.done_count >= 3 THEN '长期坚持型' END,
           CASE g.pref_category
               WHEN '校园服务' THEN '校园服务型'
               WHEN '社区服务' THEN '社区服务型'
               WHEN '环保公益' THEN '环保行动型'
               WHEN '大型赛事' THEN '大型活动型'
               WHEN '助老服务' THEN '助老服务型'
               WHEN '文化传播' THEN '文化传播型'
           END), ''),
       '该同学累计参与志愿活动 ' || g.done_count ||
       ' 次，累计有效志愿时长 ' || si.total_duration || ' 小时。'
FROM _stu_agg g
JOIN student_info si ON si.id = g.student_id;

-- =============================================================
-- 十二、修复自增序列
-- 本脚本对学生/活动/用户显式指定了 id，必须重置序列，
-- 否则后续通过接口新增的记录会撞主键。
-- =============================================================
SELECT setval(pg_get_serial_sequence('sys_user',           'id'), COALESCE((SELECT MAX(id) FROM sys_user),           1));
SELECT setval(pg_get_serial_sequence('student_info',       'id'), COALESCE((SELECT MAX(id) FROM student_info),       1));
SELECT setval(pg_get_serial_sequence('volunteer_activity', 'id'), COALESCE((SELECT MAX(id) FROM volunteer_activity), 1));

-- =============================================================
-- 十三、自检（带断言）
--
-- 04 末尾的自检是注释掉的手工查询，没有断言 —— 行数对不对只能靠肉眼。
-- 这里写成 DO 块，不满足就 RAISE EXCEPTION，整个事务回滚，
-- 不会留下「看起来跑完了、其实数据不自洽」的半套数据。
-- =============================================================
DO $$
DECLARE
    v_students      bigint;
    v_activities    bigint;
    v_signups       bigint;
    v_hours_sum     numeric;
    v_hours_approved numeric;
    v_college_hours numeric;
    v_levels        int;
    v_over_capacity int;
    v_orphan_dur    int;
    v_demo_hours    numeric;
    v_demo_tags     text;
BEGIN
    SELECT COUNT(*) INTO v_students FROM student_info WHERE deleted = 0;
    SELECT COUNT(*) INTO v_activities FROM volunteer_activity WHERE deleted = 0;
    SELECT COUNT(*) INTO v_signups FROM activity_signup WHERE deleted = 0;

    -- 1) 规模达标
    IF v_activities < 386 THEN
        RAISE EXCEPTION '[07 自检] 活动数 % 少于目标 386', v_activities;
    END IF;
    IF v_students < 1000 THEN
        RAISE EXCEPTION '[07 自检] 学生数 % 少于目标 1000', v_students;
    END IF;

    -- 2) 时长的三处口径必须相等：student_info 汇总 = APPROVED 明细合计 = 学院合计
    --    （analytics 的 hours 卡片读第一处、学院排名读第三处，不等就会自相矛盾）
    SELECT COALESCE(SUM(total_duration), 0) INTO v_hours_sum
    FROM student_info WHERE deleted = 0;

    SELECT COALESCE(SUM(duration), 0) INTO v_hours_approved
    FROM service_duration WHERE deleted = 0 AND status = 'APPROVED';

    IF v_hours_sum <> v_hours_approved THEN
        RAISE EXCEPTION '[07 自检] 时长口径不一致：student_info 合计 % ≠ APPROVED 明细合计 %',
            v_hours_sum, v_hours_approved;
    END IF;

    SELECT COALESCE(SUM(si.total_duration), 0) INTO v_college_hours
    FROM student_info si
    WHERE si.deleted = 0 AND si.college IS NOT NULL AND si.college <> '';

    IF v_college_hours <> v_hours_sum THEN
        RAISE EXCEPTION '[07 自检] 有学生的学院为空，学院排名合计 % ≠ 总时长 %',
            v_college_hours, v_hours_sum;
    END IF;

    -- 3) 六档等级都要有人，否则画像页的等级分布图会缺档
    SELECT COUNT(DISTINCT public_welfare_level) INTO v_levels
    FROM student_info WHERE deleted = 0;
    IF v_levels < 6 THEN
        RAISE EXCEPTION '[07 自检] 公益等级只出现 % 档（应 6 档），等级分布会缺档', v_levels;
    END IF;

    -- 4) 已报名人数不得超过名额
    --    名额下限 46 > 第五节报名数上限 45，本条恒成立
    SELECT COUNT(*) INTO v_over_capacity
    FROM volunteer_activity
    WHERE deleted = 0 AND signed_count > max_count;
    IF v_over_capacity > 0 THEN
        RAISE EXCEPTION '[07 自检] 有 % 场活动的已报名人数超过名额', v_over_capacity;
    END IF;

    -- 5) 服务时长必须挂在真实存在的报名上（signup_id 是 NOT NULL UNIQUE）
    SELECT COUNT(*) INTO v_orphan_dur
    FROM service_duration sd
    LEFT JOIN activity_signup sg ON sg.id = sd.signup_id
    WHERE sd.deleted = 0 AND sg.id IS NULL;
    IF v_orphan_dur > 0 THEN
        RAISE EXCEPTION '[07 自检] 有 % 条服务时长找不到对应报名', v_orphan_dur;
    END IF;

    -- 6) 演示账号（学生 1）必须有可展示的时长与标签，否则学生端主线是空的
    SELECT COALESCE(total_duration, 0) INTO v_demo_hours
    FROM student_info WHERE id = 1;
    IF v_demo_hours <= 0 THEN
        RAISE EXCEPTION '[07 自检] 演示账号 student（学生 1）累计时长仍为 0，学生端「我的时长」会是空的';
    END IF;

    SELECT tags INTO v_demo_tags FROM student_profile WHERE student_id = 1;
    IF v_demo_tags IS NULL OR v_demo_tags = '' THEN
        RAISE EXCEPTION '[07 自检] 演示账号 student（学生 1）没有画像标签，画像页会是空的';
    END IF;

    RAISE NOTICE '[07 自检] 全部通过：学生 % 人、活动 % 场、报名 % 条、累计时长 % 小时（演示账号 % 小时 / 标签 %）',
        v_students, v_activities, v_signups, v_hours_sum, v_demo_hours, v_demo_tags;
END $$;

COMMIT;

-- =============================================================
-- 执行后建议手工核对（可选）
-- =============================================================
-- SELECT COUNT(*) AS students FROM student_info WHERE deleted = 0;
-- SELECT COUNT(*) AS activities FROM volunteer_activity WHERE deleted = 0;
-- SELECT COUNT(*) AS signups FROM activity_signup WHERE deleted = 0;
-- SELECT public_welfare_level, COUNT(*) FROM student_info
--   WHERE deleted = 0 GROUP BY public_welfare_level ORDER BY 2 DESC;
-- SELECT si.college, ROUND(SUM(si.total_duration), 1) AS hours, COUNT(*) AS students
--   FROM student_info si WHERE si.deleted = 0 GROUP BY si.college ORDER BY hours DESC;
