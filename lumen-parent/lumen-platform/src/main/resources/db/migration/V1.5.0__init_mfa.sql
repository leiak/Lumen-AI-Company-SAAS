-- MFA (TOTP) enrollment table.
-- Cross-tenant by design — MFA enrollment is keyed by user_id, not tenant_id, because
-- the same human user must be able to authenticate across multiple tenants within the
-- org without re-enrolling their authenticator. The mapper must be annotated with
-- @InterceptorIgnore(tenantLine = "true") to bypass the tenant-row interceptor.

CREATE TABLE IF NOT EXISTS sys_user_mfa (
    user_id        BIGINT          NOT NULL,
    secret         VARCHAR(64)     NOT NULL COMMENT 'base32 TOTP secret (no padding)',
    enabled        TINYINT         NOT NULL DEFAULT 0 COMMENT '0=pending/enrolled, 1=verified+active',
    backup_codes   VARCHAR(512)    DEFAULT NULL COMMENT 'comma-separated one-time backup codes',
    enrolled_at    DATETIME        DEFAULT NULL COMMENT 'first successful verify timestamp',
    create_by      BIGINT          DEFAULT NULL,
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      BIGINT          DEFAULT NULL,
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA enrollments (TOTP)';

-- mfa_required: server-driven "force MFA on next login" flag, set by admin policy.
-- Coexists with the legacy mfa_secret/mfa_enabled columns on sys_user, which remain
-- unused for now; the new flow uses sys_user_mfa exclusively.
ALTER TABLE sys_user
    ADD COLUMN mfa_required TINYINT NOT NULL DEFAULT 0 COMMENT 'force MFA on next login (admin policy)';