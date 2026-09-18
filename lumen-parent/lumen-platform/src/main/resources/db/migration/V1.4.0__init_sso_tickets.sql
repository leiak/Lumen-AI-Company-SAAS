-- SSO 票据表：中央认证签发的不透明随机票据，供下游应用一次性换取 JWT。
CREATE TABLE sys_sso_ticket (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    ticket              VARCHAR(64)     NOT NULL COMMENT '票据字符串，Base64 URL 32 随机字节',
    user_id             BIGINT          NOT NULL COMMENT '票据所属用户',
    tenant_id           BIGINT          NOT NULL COMMENT '票据所属租户',
    app_id              VARCHAR(32)     NOT NULL COMMENT '目标应用标识，消费时必须一致',
    expires_at          DATETIME        NOT NULL COMMENT '票据到期时间',
    consumed_at         DATETIME        DEFAULT NULL COMMENT '消费时间，NULL = 未消费',
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_ticket (ticket),
    KEY idx_sso_ticket_app (app_id, expires_at),
    KEY idx_sso_ticket_user (user_id, expires_at),
    KEY idx_ticket_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 票据';