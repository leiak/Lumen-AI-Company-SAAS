package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * One MFA backup code, stored as a single bcrypt hash. Each row represents one
 * single-use recovery code issued to the user. The plaintext is shown to the user
 * exactly once at enrollment ({@code MfaService#confirm}) and is never persisted.
 *
 * <p>Cross-tenant by design (see {@code sys_user_mfa_backup_code} migration comment);
 * the mapper is annotated with {@code @InterceptorIgnore(tenantLine = "true")}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_mfa_backup_code")
public class SysUserMfaBackupCode extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Owner of the code (matches {@code sys_user_mfa.user_id}). */
    private Long userId;

    /** bcrypt hash of the 8-char plaintext code. ~60 chars at default cost. */
    private String codeHash;

    /** Set to NOW() when the code is successfully redeemed. NULL = still unused. */
    private LocalDateTime usedAt;

    /** Client IP recorded at redemption for audit. Best-effort — NULL is acceptable. */
    private String usedIp;
}