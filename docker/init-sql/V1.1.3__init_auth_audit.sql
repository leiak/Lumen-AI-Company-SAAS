DROP TABLE IF EXISTS sys_auth_audit;
CREATE TABLE sys_auth_audit (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          DEFAULT NULL,
    user_name           VARCHAR(50)     DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    action              VARCHAR(50)     NOT NULL COMMENT 'LOGIN/LOGOUT/REFRESH/PWD_CHANGE/MFA_BIND/SSO_BIND/TOKEN_REVOKE',
    -- status: TINYINT (boolean-like 1=success 0=fail); differs from sys_user.status CHAR(1) by design
    status              TINYINT         NOT NULL DEFAULT 1,
    ip                  VARCHAR(64)     DEFAULT NULL,
    user_agent          VARCHAR(500)    DEFAULT NULL,
    detail              VARCHAR(1000)   DEFAULT NULL,
    audit_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_audit_user (user_id, audit_at),
    KEY idx_auth_audit_action (action, audit_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证审计日志';
