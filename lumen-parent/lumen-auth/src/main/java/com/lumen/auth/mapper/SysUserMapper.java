package com.lumen.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for {@code sys_user}. The user table itself is tenant-scoped at the row level
 * (the tenant interceptor rewrites queries to the current tenant), so most default
 * operations are safe. However, {@link #findByUserId(Long)} looks up by the global
 * {@code user_id} alone — that lookup must work across tenants (e.g. when the SSO
 * exchange or MFA step-up flow hands back a userId from a token claim, or when the
 * MFA flow looks up the user to complete the verify step). The class-level
 * {@link InterceptorIgnore} mirrors {@code SysUserMfaMapper} and {@code TicketMapper}.
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SysUserMapper extends BaseMapper<SysUser> {
    default SysUser findByTenantAndUsername(Long tenantId, String userName) {
        return selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getTenantId, tenantId)
            .eq(SysUser::getUserName, userName)
            .eq(SysUser::getDeleted, 0)
            .last("LIMIT 1"));
    }

    /**
     * 按 userId 查找未删除的用户；供 SSO 兑换时填充 JWT 的 userName/nickName。
     */
    default SysUser findByUserId(Long userId) {
        if (userId == null) return null;
        return selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserId, userId)
            .eq(SysUser::getDeleted, 0)
            .last("LIMIT 1"));
    }

    default void incrFailCount(Long userId) {
        update(null, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getUserId, userId)
            .setSql("fail_count = fail_count + 1"));
    }

    default void resetFailCount(Long userId) {
        update(null, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getUserId, userId)
            .set(SysUser::getFailCount, 0)
            .set(SysUser::getLockUntil, null)
            .set(SysUser::getLoginIp, null));
    }

    default void lockUntil(Long userId, LocalDateTime until) {
        update(null, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getUserId, userId)
            .set(SysUser::getLockUntil, until));
    }

    /**
     * Look up all {@code role_key} values bound to a user through sys_user_role.
     * The role_key (e.g. "super_admin", "admin", "hr_admin") is what gets stuffed into
     * the JWT "roles" claim and matched by Spring Security {@code hasRole(...)}.
     *
     * <p>Must be an <b>abstract</b> method (not a {@code default} method) — MyBatis
     * only reads {@code @Select}/{@code @Insert}/{@code @Update}/{@code @Delete}
     * annotations on the abstract interface methods, not on Java default methods.</p>
     */
    @Select("SELECT r.role_key FROM sys_user_role ur "
        + "JOIN sys_role r ON r.role_id = ur.role_id "
        + "WHERE ur.user_id = #{userId} AND r.status = '0' AND r.deleted = 0")
    List<String> findRoleKeysByUserId(@Param("userId") Long userId);
}