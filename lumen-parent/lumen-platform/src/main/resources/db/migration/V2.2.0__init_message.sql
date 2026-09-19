-- ============================================================
-- V2.2.0 — P3: message center tables (channel / template / notification / subscription)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 消息渠道
CREATE TABLE msg_channel (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)     NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    type            VARCHAR(16)     NOT NULL                COMMENT 'email/sms/site/dingtalk/wechat/webhook',
    config          JSON            DEFAULT NULL            COMMENT 'SMTP host, app secret, etc.',
    enabled         TINYINT         NOT NULL DEFAULT 1,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_tenant (code, tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息渠道';

-- 消息模板
CREATE TABLE msg_template (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)     NOT NULL,
    channel_code    VARCHAR(32)     NOT NULL,
    subject         VARCHAR(255)    DEFAULT NULL,
    content         TEXT            NOT NULL,
    variables       JSON            DEFAULT NULL            COMMENT '变量列表 [name, code]',
    enabled         TINYINT         NOT NULL DEFAULT 1,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_channel_tenant (code, channel_code, tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板';

-- 消息通知 (inbox + send log)
CREATE TABLE msg_notification (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    channel_code        VARCHAR(32)     NOT NULL,
    template_code       VARCHAR(64)     DEFAULT NULL,
    recipient_user_id   BIGINT          DEFAULT NULL,
    recipient_address   VARCHAR(255)    DEFAULT NULL    COMMENT 'email/phone/webhook url',
    subject             VARCHAR(255)    DEFAULT NULL,
    content             TEXT            DEFAULT NULL,
    status              TINYINT         NOT NULL DEFAULT 0    COMMENT '0=pending 1=sent 2=failed 3=read',
    retry_count         INT             NOT NULL DEFAULT 0,
    error_message       VARCHAR(512)    DEFAULT NULL,
    sent_time           DATETIME        DEFAULT NULL,
    read_time           DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_recipient_status (recipient_user_id, status, create_time),
    INDEX idx_status (status, create_time),
    INDEX idx_template (template_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知';

-- 用户订阅
CREATE TABLE msg_subscription (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    event_type      VARCHAR(64)     NOT NULL                COMMENT 'workflow.task.created, finance.expense.approved, ...',
    channel_code    VARCHAR(32)     NOT NULL,
    enabled         TINYINT         NOT NULL DEFAULT 1,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_event_channel (user_id, event_type, channel_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅';