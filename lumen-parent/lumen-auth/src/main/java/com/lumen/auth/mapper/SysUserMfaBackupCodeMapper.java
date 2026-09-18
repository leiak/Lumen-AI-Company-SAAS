package com.lumen.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.auth.entity.SysUserMfaBackupCode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for {@code sys_user_mfa_backup_code}.
 *
 * <p><b>Must skip the tenant-line interceptor.</b> Backup codes inherit the cross-tenant
 * semantics of {@code sys_user_mfa} — a single human enrolls once and authenticates into
 * any tenant they belong to. The same user must be able to redeem a backup code in any
 * tenant context. Same rationale as {@link SysUserMfaMapper} and {@link com.lumen.common.sso.TicketMapper}.</p>
 *
 * <p>All non-CRUD operations use explicit {@code @Select}/{@code @Update}/{@code @Delete}
 * rather than {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}. This keeps the
 * service pure-unit-testable with plain Mockito stubs (the lambda-cache lookup that
 * MyBatis-Plus uses for column resolution needs Spring context to register the table
 * meta, which is unavailable in pure unit tests).</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SysUserMfaBackupCodeMapper extends BaseMapper<SysUserMfaBackupCode> {

    /**
     * All unused backup codes for the user (used_at IS NULL, not logically deleted).
     * Bounded by the per-user enrollment (10 rows) — safe to scan.
     */
    @Select("SELECT id, user_id, code_hash, used_at, used_ip, " +
        "create_by, create_time, update_by, update_time, deleted " +
        "FROM sys_user_mfa_backup_code " +
        "WHERE user_id = #{userId} AND used_at IS NULL AND deleted = 0")
    List<SysUserMfaBackupCode> selectUnusedByUserId(@Param("userId") Long userId);

    /**
     * Atomic single-use redemption: mark a code used only if it is currently unused.
     * Returns 1 on first successful claim, 0 if another request already won the race
     * or the code has been deleted.
     */
    @Update("UPDATE sys_user_mfa_backup_code " +
        "SET used_at = NOW(), used_ip = #{usedIp}, " +
        "    update_time = NOW(), update_by = #{userId} " +
        "WHERE id = #{id} AND user_id = #{userId} AND used_at IS NULL AND deleted = 0")
    int markUsed(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("usedIp") String usedIp);

    /**
     * Hard-delete every unused row for the user — used by disable() and by re-confirm
     * to wipe stale hashes from prior enrollments.
     */
    @Delete("DELETE FROM sys_user_mfa_backup_code " +
        "WHERE user_id = #{userId} AND used_at IS NULL")
    int deleteAllUnusedByUserId(@Param("userId") Long userId);

    /**
     * Hard-delete every row for the user — used by confirm() to ensure no stale hashes
     * survive a re-enrollment.
     */
    @Delete("DELETE FROM sys_user_mfa_backup_code " +
        "WHERE user_id = #{userId}")
    int deleteAllByUserId(@Param("userId") Long userId);
}