-- =============================================================
-- 高校志愿服务时长认证与公益画像数据分析系统
-- 脚本 11：活动图文说明字段（增量、可重复执行）
-- 数据库类型：PostgreSQL 16+
-- =============================================================
--
-- 目标：
--   1. attachment 补齐 MIME、图片说明、展示顺序；
--   2. 增加按业务对象读取图片的顺序索引；
--   3. 同步 ACTIVITY 业务类型的中文标签。
--
-- 文件本体不存 PostgreSQL，只存 /uploads/... 地址；图片本体由后端上传目录
-- 或生产环境 Nginx 静态目录提供。
-- =============================================================

SET client_encoding = 'UTF8';

ALTER TABLE attachment ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS caption      VARCHAR(255);
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS sort_order   INT DEFAULT 0;

COMMENT ON COLUMN attachment.content_type IS 'MIME 类型，如 image/png';
COMMENT ON COLUMN attachment.caption      IS '图片说明';
COMMENT ON COLUMN attachment.sort_order   IS '同一业务下的展示顺序，从 0 开始';

CREATE INDEX IF NOT EXISTS idx_attachment_biz_sort
    ON attachment (biz_type, biz_id, sort_order, id);

UPDATE sys_dict
   SET dict_value = '活动图片'
 WHERE dict_type = 'attachment_biz_type'
   AND dict_key = 'ACTIVITY';

INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort, status)
VALUES
    ('attachment_biz_type', 'ACTIVITY', '活动图片', 1, 1),
    ('attachment_biz_type', 'ORG',      '组织资质', 2, 1),
    ('attachment_biz_type', 'AVATAR',   '用户头像', 3, 1)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- 自检：三个字段与索引都应存在。
SELECT COUNT(*) AS attachment_image_columns
  FROM information_schema.columns
 WHERE table_name = 'attachment'
   AND column_name IN ('content_type', 'caption', 'sort_order');

SELECT COUNT(*) AS attachment_sort_index
  FROM pg_indexes
 WHERE tablename = 'attachment'
   AND indexname = 'idx_attachment_biz_sort';
