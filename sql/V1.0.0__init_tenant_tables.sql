-- ============================================================
-- 租户主表
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    short_name      VARCHAR(50)     DEFAULT '',
    contact_name    VARCHAR(50)     DEFAULT '',
    contact_phone   VARCHAR(50)     DEFAULT '',
    contact_email   VARCHAR(100)    DEFAULT '',
    industry        VARCHAR(50)     DEFAULT '',
    scale           VARCHAR(20)     DEFAULT '',
    region          VARCHAR(50)     DEFAULT '',
    package_id      BIGINT          NOT NULL DEFAULT 1,
    status          TINYINT         NOT NULL DEFAULT 1,
    trial_days      INT             NOT NULL DEFAULT 30,
    expire_at       DATETIME        DEFAULT NULL,
    activated_at    DATETIME        DEFAULT NULL,
    logo_url        VARCHAR(500)    DEFAULT NULL,
    description     VARCHAR(500)    DEFAULT NULL,
    create_by       BIGINT          NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          NOT NULL DEFAULT 0,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (code, deleted),
    KEY idx_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户主表';

CREATE TABLE IF NOT EXISTS tenant_package (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    modules         JSON            NOT NULL,
    max_users       INT             NOT NULL DEFAULT 10,
    max_storage_gb  INT             NOT NULL DEFAULT 5,
    max_employees   INT             NOT NULL DEFAULT 100,
    price_cents     BIGINT          NOT NULL DEFAULT 0,
    duration_days   INT             NOT NULL DEFAULT 365,
    description     VARCHAR(500)    DEFAULT NULL,
    is_builtin      TINYINT         NOT NULL DEFAULT 0,
    create_by       BIGINT          NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          NOT NULL DEFAULT 0,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_package_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐定义';

CREATE TABLE IF NOT EXISTS tenant_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT          NOT NULL,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    TEXT            DEFAULT NULL,
    value_type      VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    create_by       BIGINT          NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          NOT NULL DEFAULT 0,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户级配置项';

CREATE TABLE IF NOT EXISTS common_seq (
    seq_name        VARCHAR(50)     NOT NULL,
    current_val     BIGINT          NOT NULL DEFAULT 1,
    step            INT             NOT NULL DEFAULT 1,
    prefix          VARCHAR(20)     DEFAULT '',
    format          VARCHAR(50)     DEFAULT '{prefix}{yyyyMMdd}{seq:6}',
    description     VARCHAR(200)    DEFAULT NULL,
    create_by       BIGINT          NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          NOT NULL DEFAULT 0,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (seq_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务流水号';
