-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 14：性能索引（只读热点的实测补强）+ 全库统计信息刷新
-- 数据库类型：PostgreSQL 16+（实测环境 18.6）
-- =============================================================
--
-- 【执行顺序】在 01 → 02 → 03 → [04] → 05 → 06 之后执行；可重复执行。
--   不建表、不改列、不动数据，只有 CREATE INDEX IF NOT EXISTS 与 ANALYZE。
--
-- 【本脚本只加"实测有据"的索引，判定口径】
--   1. 先用 EXPLAIN (ANALYZE, BUFFERS) 跑热点查询，记录扫描方式 / 实际时间 / buffers；
--   2. 在事务里 CREATE INDEX → ANALYZE → 重跑同一条 EXPLAIN → ROLLBACK（实验不留痕），
--      只有规划器**确实改用新索引**且时间/buffers 下降的才写进本脚本；
--   3. 不建与既有索引同访问路径的索引（02 第六节 + 05 + 06 的既有索引清单；
--      同最左列且同样能覆盖谓词的不重复建）。
--
-- 【为什么是 partial index（WHERE deleted = 0）而不是全量索引】
--   本系统所有业务查询都带 deleted = 0。全量索引（含已删除行）无法做 Index Only Scan
--   直接回答 COUNT(*) —— 需要回表确认可见性；partial 索引只含活行，规划器实测会选择
--   Index Only Scan 且 Heap Fetches: 0，这正是下面两条索引的收益来源。
--
-- 【实测环境与噪声说明】
--   库是**共享云库**（103.40.14.100，PostgreSQL 18.6，shared_buffers=128MB），
--   同一实例还有其它客户端在访问，同一条查询的耗时会有 2~4 倍抖动
--   （实测 selectOverviewMetrics：热态 4.2ms / 冷态 16.3ms）。因此下面的对比数字
--   取"连续 3 次 EXPLAIN 的中位数"，并同时给出 buffers（与缓存无关，更稳定）。
--
-- 【已知问题（不在本脚本处理，见性能报告"需要人工决定"清单）】
--   · ~~activity_category 缺 remark 列~~ —— **2026-09-27 已修**：该补列语句原先写在
--     已删除的 07_demo_scale.sql 里，导致新环境重建库后 GET /api/v1/categories 恒 500；
--     现已并入 06_backend_gap_fix2.sql（`ALTER TABLE activity_category ADD COLUMN IF NOT EXISTS remark`），
--     云库也已补列并实测接口恢复。本条保留作记录，不再需要处理。
--   · AnalyticsMapper.selectOrgStats 是 11 个组织各扫一遍签到/时长表（实测 4.9~19.7ms，
--     buffers 698，是看板最贵的一条），属 SQL 结构问题，加索引无解，需改写为一次聚合；
--     2026-09-27 已由单独一轮改动改写（见该 mapper 的注释与提交记录），本脚本不含该改动。
-- =============================================================

SET client_encoding = 'UTF8';

-- -------------------------------------------------------------
-- 一、看板「累计志愿时长」指标（AnalyticsMapper.selectOverviewMetrics）
-- -------------------------------------------------------------
-- SQL 形态：SELECT ROUND(COALESCE(SUM(total_duration), 0), 1) FROM student_info WHERE deleted = 0
-- 加索引前：Seq Scan on student_info，shared hit=58
--           同一语句连跑 10 次：0.187 / 0.495（中位）/ 0.856 ms，忽快忽慢
-- 加索引后：Index Only Scan using idx_student_total_duration_active，Heap Fetches: 0，shared hit=5
--           同一语句连跑 10 次：0.185 / 0.187（中位）/ 0.230 ms
-- 收益：中位耗时 0.495 → 0.187 ms（约 2.7 倍），扫描页 58 → 5（约 12 倍），且不再出现 0.8ms 尖刺
-- 同表既有索引：student_info_pkey(id)、student_info_user_id_key(user_id)、
--   student_info_student_no_key(student_no)、idx_student_college(college)，
--   最左列都不是 total_duration，不构成重复。
-- 顺带收益：本查询也是 Q4 学院排名、以及任何"未删除学生时长合计"的底座。
CREATE INDEX IF NOT EXISTS idx_student_total_duration_active
    ON student_info (total_duration)
    WHERE deleted = 0;

-- -------------------------------------------------------------
-- 二、看板「报名总人数」指标的两个子查询（AnalyticsMapper.selectOverviewMetrics）
-- -------------------------------------------------------------
-- SQL 形态：
--   SELECT COUNT(*) FROM activity_signup WHERE deleted = 0
--   SELECT COUNT(*) FROM activity_signup WHERE deleted = 0 AND signup_time < date_trunc('month', CURRENT_DATE)
-- 加索引前（本期 COUNT）：Seq Scan on activity_signup，shared hit=37，
--   连跑 10 次 1.338 / 1.357（中位）/ 1.485 ms
-- 加索引前（基期 COUNT）：Index Scan using idx_signup_time + 回表，shared hit=142，
--   连跑 10 次 0.854 / 0.884（中位）/ 1.281 ms
-- 加索引后（本期 COUNT）：Index Only Scan using idx_signup_signup_time_active，Heap Fetches: 0，
--   shared hit=5，连跑 10 次 0.265 / 0.291（中位）/ 0.325 ms（约 4.7 倍）
-- 加索引后（基期 COUNT）：Index Only Scan，Heap Fetches: 0，shared hit=4，
--   连跑 10 次 0.173 / 0.174（中位）/ 0.191 ms（约 5.1 倍，扫描页 142 → 4）
-- 与既有 idx_signup_time(signup_time) 的关系：最左列相同，但 idx_signup_time 是全量索引，
--   规划器不会选它做 Index Only Scan（实测基期子查询走的是它 + 回表 142 buffers）；
--   partial 版本把"活行且带 signup_time"收进索引本身，才出现 Index Only Scan。
--   两者服务不同访问路径，不是重复建；写入侧 INSERT 会多维护一个索引，代价已权衡。
CREATE INDEX IF NOT EXISTS idx_signup_signup_time_active
    ON activity_signup (signup_time)
    WHERE deleted = 0;

-- -------------------------------------------------------------
-- 三、以下候选经实测**未采用**（写明理由，避免后来人重复试）
-- -------------------------------------------------------------
-- · activity_signup (activity_id, id DESC)：想优化"组织端报名列表 LIMIT 10"，
--   实测规划器仍选 activity_signup_pkey 倒序 + 过滤（2058 行出 10 行，0.92 ms 未变），
--   索引未被选中，不建。
-- · activity_signup (status, id DESC)：实测仅在"组织 + 状态"同时筛选时被选中，
--   而当前数据下与主键倒序方案时间相同（0.09 ms vs 0.11 ms），证据不足，不建。
-- · attendance_record (sign_in_time) / (status, sign_in_time) 的 partial 版本：
--   规划器确实改用 Index Only Scan，但实测 0.32 ms vs 0.30 ms，落在云库抖动区间内，
--   不值一条索引，不建。
-- · service_duration (status) partial 覆盖索引：同上，0.80 ms vs 0.49~0.91 ms，不建。
-- · 全库 18 个外键已全部有以首列开头的索引（06 第十节第 5 项自检，实测 18/18 = true），
--   无需补外键索引。
-- · 大表上的 LIKE '%关键字%'（活动名/姓名/学号搜索）无法走 btree，需要 pg_trgm 扩展，
--   见报告"需要人工决定"清单（云库是否允许 CREATE EXTENSION 需人工确认）。

-- -------------------------------------------------------------
-- 四、刷新统计信息（16 张表全量 ANALYZE）
-- -------------------------------------------------------------
-- 背景：实测 pg_stat_user_tables 里 operation_log / org_info / activity_category /
-- sys_role / volunteer_activity / notification 的 last_analyze 为 NULL，
-- 其中 volunteer_activity / notification / sys_role 连自动 ANALYZE 都没跑过
-- （pg_class.reltuples = -1），规划器用的是默认行数估计。
-- 本脚本只跑 ANALYZE，不改数据，可重复执行、开销极小（全库合计 < 1 秒）。
ANALYZE sys_user;
ANALYZE sys_role;
ANALYZE sys_user_role;
ANALYZE student_info;
ANALYZE sys_dict;
ANALYZE notification;
ANALYZE operation_log;
ANALYZE attachment;
ANALYZE org_info;
ANALYZE activity_category;
ANALYZE volunteer_activity;
ANALYZE activity_signup;
ANALYZE attendance_record;
ANALYZE service_duration;
ANALYZE duration_audit;
ANALYZE student_profile;

-- -------------------------------------------------------------
-- 五、自检：确认索引已就位、统计信息已刷新
-- -------------------------------------------------------------
SELECT 'idx_student_total_duration_active' AS item,
       COUNT(*) AS ok
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname = 'idx_student_total_duration_active'
UNION ALL
SELECT 'idx_signup_signup_time_active',
       COUNT(*)
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname = 'idx_signup_signup_time_active'
UNION ALL
SELECT '16 表已有统计信息（期望 16）',
       COUNT(*)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public'
   AND c.relkind = 'r'
   -- 只看 reltuples：ANALYZE 过就是 >= 0，没分析过是 -1。
   -- ⚠️ 不要在这里查 pg_statistic —— 它只对超级用户/pg_read_all_stats 可见，
   -- 而本脚本在部署脚本里是以应用角色（非超级用户）执行的，会直接
   -- "permission denied for table pg_statistic" 把整条链打断（真机实测踩到）。
   AND c.reltuples >= 0;

-- 期望：前两行 ok = 1，第三行 16
