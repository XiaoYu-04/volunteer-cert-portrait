-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 1/4：创建数据库
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 说明：
--   CREATE DATABASE 不能写在事务里，也没有 IF NOT EXISTS 语法，
--   因此本脚本重复执行会报 "database already exists"，这是预期行为，忽略即可。
--
--   TEMPLATE template0 是为了在实例的 template1 编码不是 UTF8 时
--   也能建出 UTF8 库；若你的实例 template1 本来就是 UTF8，去掉这行也可以。
--
-- 执行方式（psql）：
--   psql -U postgres -h <主机> -p <端口> -f 01_create_database.sql
-- =============================================================

CREATE DATABASE volunteer_cert_portrait
    WITH OWNER    = postgres
         ENCODING = 'UTF8'
         TEMPLATE = template0;

-- -------------------------------------------------------------
-- 验证（在 psql 中手动执行）
-- -------------------------------------------------------------
-- \l volunteer_cert_portrait      -- 查看库是否创建成功
-- \c volunteer_cert_portrait      -- 切换到该库
-- \dt                             -- 此时应为空，执行 02_schema.sql 后才有表