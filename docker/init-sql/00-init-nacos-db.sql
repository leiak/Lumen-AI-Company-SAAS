-- Nacos own database (separate from lumen_db) — created automatically by MySQL
-- because docker-entrypoint-initdb.d runs against every fresh data dir. We
-- need this when Nacos runs in standalone MySQL mode (our compose does).
CREATE DATABASE IF NOT EXISTS `nacos` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON `nacos`.* TO 'root'@'%';
FLUSH PRIVILEGES;
