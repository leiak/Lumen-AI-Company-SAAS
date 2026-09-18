DROP TABLE IF EXISTS sys_sso_account;
CREATE TABLE sys_sso_account (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    platform            VARCHAR(20)     NOT NULL COMMENT 'dingtalk/wechatwork/feishu/ldap/oidc',
    open_id             VARCHAR(200)    NOT NULL,
    union_id            VARCHAR(200)    DEFAULT NULL,
    bind_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at       DATETIME        DEFAULT NULL,
    create_by           BIGINT          NOT NULL DEFAULT 0,
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          NOT NULL DEFAULT 0,
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_platform_open (platform, open_id, deleted),
    KEY idx_sso_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 账号绑定';

DROP TABLE IF EXISTS sys_user_session;
CREATE TABLE sys_user_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_id          VARCHAR(64)     NOT NULL COMMENT 'Session ID = JWT jti',
    user_id             BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    refresh_token       VARCHAR(500)    DEFAULT NULL,
    ip                  VARCHAR(64)     DEFAULT NULL,
    user_agent          VARCHAR(500)    DEFAULT NULL,
    device              VARCHAR(50)     NOT NULL DEFAULT 'WEB' COMMENT 'WEB/H5/APP/MINI',
    login_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at           DATETIME        NOT NULL,
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '1-有效 0-失效',
    logout_at           DATETIME        DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_session_user (user_id),
    KEY idx_session_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话';

DROP TABLE IF EXISTS sys_login_fail;
CREATE TABLE sys_login_fail (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_name           VARCHAR(50)     NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    ip                  VARCHAR(64)     DEFAULT NULL,
    user_agent          VARCHAR(500)    DEFAULT NULL,
    fail_reason         VARCHAR(200)    DEFAULT NULL,
    fail_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_fail_user (user_name, tenant_id, fail_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录失败审计';
