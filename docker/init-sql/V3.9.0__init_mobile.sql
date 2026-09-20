-- ============================================================
-- V3.9.0 — P4-B3.3: mobile + integration module tables (mob_* + int_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- Module: lumen-mobile (AppVersion / PushToken / Attendance / IntegrationAppConfig / IntegrationEventLog)
-- ============================================================

-- 1) 移动 App 版本
CREATE TABLE mob_app_version (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    platform                VARCHAR(16)     NOT NULL                COMMENT 'android/ios/harmony',
    version                 VARCHAR(20)     NOT NULL                COMMENT 'semantic version (e.g. 1.0.0)',
    build_number            INT             NOT NULL DEFAULT 0,
    force_update            TINYINT         NOT NULL DEFAULT 0       COMMENT '0=不强制 1=强制',
    min_supported_version   VARCHAR(20)     DEFAULT NULL,
    download_url            VARCHAR(512)    NOT NULL,
    release_notes           TEXT            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/released/deprecated',
    released_at             DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_version_platform_version (platform, version, deleted),
    KEY idx_app_version_platform_status (platform, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='移动 App 版本';

-- 2) 推送 Token
-- 安全要求 #3: push_token_enc 加密 (AES-256-GCM); token_hash 存 md5(token) 作为查询索引
CREATE TABLE mob_push_token (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT          NOT NULL,
    platform                VARCHAR(16)     NOT NULL                COMMENT 'android/ios/harmony',
    device_id               VARCHAR(128)    NOT NULL,
    push_token_enc          VARCHAR(1024)   NOT NULL                COMMENT 'AES-256-GCM 加密',
    token_hash              VARCHAR(64)     NOT NULL                COMMENT 'md5(token) 32 hex 字符',
    app_version             VARCHAR(32)     DEFAULT NULL,
    device_model            VARCHAR(64)     DEFAULT NULL,
    os_version              VARCHAR(32)     DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    last_active_at          DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #9: 同一平台同一设备只一条非删除记录
    UNIQUE KEY uk_push_token_platform_device (platform, device_id, deleted),
    -- token_hash 唯一索引 — register 防重复 / 查询索引
    UNIQUE KEY uk_push_token_platform_hash (platform, token_hash, deleted),
    KEY idx_push_token_user_status (tenant_id, user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送 Token';

-- 3) 移动考勤打卡
-- 安全要求 #14: 一天一次 (user_id, type, DATE(clocked_at))
-- 安全要求 #16: photo_url VARCHAR(512)
CREATE TABLE mob_attendance (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT          NOT NULL,
    type                        VARCHAR(16)     NOT NULL                COMMENT 'clock_in/clock_out',
    latitude                    DECIMAL(10,7)   DEFAULT NULL,
    longitude                   DECIMAL(10,7)   DEFAULT NULL,
    address                     VARCHAR(512)    DEFAULT NULL,
    photo_url                   VARCHAR(512)    DEFAULT NULL            COMMENT 'VARCHAR(512); 超长 service 拒绝',
    device_id                   VARCHAR(128)    NOT NULL,
    distance_from_office_m      INT             DEFAULT NULL            COMMENT 'GPS 距离办公室米数',
    status                      VARCHAR(16)     NOT NULL DEFAULT 'normal' COMMENT 'normal/late/early/absent',
    clocked_at                  DATETIME        NOT NULL,
    tenant_id                   BIGINT          NOT NULL DEFAULT 0,
    create_by                   BIGINT          DEFAULT NULL,
    create_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by                   BIGINT          DEFAULT NULL,
    update_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                     TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #14: UNIQUE(user_id, type, DATE(clocked_at), deleted)
    UNIQUE KEY uk_attendance_user_type_date (user_id, type, deleted, clocked_at),
    KEY idx_attendance_user_clocked (tenant_id, user_id, clocked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='移动考勤打卡';

-- 4) 协作平台应用配置
-- 安全要求 #4: app_secret_enc + webhook_url_enc 字段级 AES-GCM 加密
CREATE TABLE int_app_config (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL,
    platform                VARCHAR(16)     NOT NULL                COMMENT 'dingtalk/wechatwork/feishu',
    app_id                  VARCHAR(128)    DEFAULT NULL,
    app_secret_enc          VARCHAR(1024)   DEFAULT NULL            COMMENT 'AES-GCM 加密',
    agent_id                VARCHAR(64)     DEFAULT NULL,
    redirect_uri            VARCHAR(512)    DEFAULT NULL,
    webhook_url_enc         VARCHAR(1024)   DEFAULT NULL            COMMENT 'AES-GCM 加密',
    enabled                 TINYINT         NOT NULL DEFAULT 1       COMMENT '0=禁用 1=启用',
    config                  JSON            DEFAULT NULL            COMMENT '扩展 JSON 配置',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_app_config_code_tenant (tenant_id, code, deleted),
    KEY idx_int_app_config_platform (tenant_id, platform, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作平台应用配置';

-- 5) 集成事件日志 (仅追加 — 安全要求 #13)
-- 安全要求 #6: UNIQUE(source_id, event_type, deleted) — 防重放
CREATE TABLE int_event_log (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    platform                VARCHAR(16)     NOT NULL                COMMENT 'dingtalk/wechatwork/feishu',
    event_type              VARCHAR(64)     NOT NULL,
    source_id               VARCHAR(128)    DEFAULT NULL            COMMENT '平台侧事件唯一 ID',
    payload                 JSON            DEFAULT NULL,
    processed               TINYINT         NOT NULL DEFAULT 0       COMMENT '0=pending 1=ok 2=failed',
    processed_at            DATETIME        DEFAULT NULL,
    error_message           VARCHAR(512)    DEFAULT NULL,
    retry_count             INT             NOT NULL DEFAULT 0,
    received_at             DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #6: 防重放
    UNIQUE KEY uk_int_event_source_type (source_id, event_type, deleted),
    KEY idx_int_event_platform_received (tenant_id, platform, received_at),
    KEY idx_int_event_processed (processed, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集成事件日志';
