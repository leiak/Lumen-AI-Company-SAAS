package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MFA (TOTP) enrollment row, one per user.
 *
 * <p>Cross-tenant by design — the userId PK is global, not scoped to a tenant, so the
 * mapper is annotated with {@code @InterceptorIgnore(tenantLine = "true")}. A single
 * human may enroll once and authenticate into any tenant they belong to.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_mfa")
public class SysUserMfa extends BaseEntity {
    /** PK is the user_id, not a synthetic id — one enrollment row per user. */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** Base32-encoded TOTP secret (no padding). */
    private String secret;

    /** 0=secret staged but not yet verified; 1=verified+active. */
    private Integer enabled;

    /** Comma-separated one-time backup codes (hashed or plaintext — see plan). */
    private String backupCodes;

    /** Timestamp of first successful confirm(). */
    private LocalDateTime enrolledAt;
}