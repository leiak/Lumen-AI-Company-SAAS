-- MFA backup codes, hashed with bcrypt.
--
-- One row per code — stored as a bcrypt hash so a database compromise does NOT yield
-- usable recovery codes. Codes are single-use: when a user redeems one, used_at is set
-- and any further attempt with the same plaintext code fails (the unhashed plaintext
-- is shown to the user exactly once at enrollment, then never again).
--
-- Cross-tenant by design: MFA enrollment is global (see sys_user_mfa comment), so a
-- user must be able to redeem a backup code into any tenant they belong to. The
-- mapper must be annotated with @InterceptorIgnore(tenantLine = "true").

CREATE TABLE IF NOT EXISTS sys_user_mfa_backup_code (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    code_hash    VARCHAR(72)  NOT NULL COMMENT 'bcrypt hash of a single backup code',
    used_at      DATETIME     DEFAULT NULL COMMENT 'first successful redemption timestamp; NULL = unused',
    used_ip      VARCHAR(64)  DEFAULT NULL COMMENT 'optional audit: client IP that consumed the code',
    create_by    BIGINT       DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    BIGINT       DEFAULT NULL,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_user (user_id),
    INDEX idx_user_unused (user_id, used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='single-use MFA backup codes (hashed)';

-- The legacy plaintext column on sys_user_mfa is no longer written; leave it in place
-- to keep the original migration idempotent. It will always be NULL going forward and
-- may be dropped in a future cleanup migration once all deployments have rolled past
-- this point.