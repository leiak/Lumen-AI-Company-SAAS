package com.lumen.auth.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.auth.entity.SysUserMfa;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@code sys_user_mfa}.
 *
 * <p><b>Must skip the tenant-line interceptor.</b> MFA enrollments are keyed by a global
 * {@code user_id} (not {@code tenant_id}), and a user must be able to authenticate into
 * any tenant they belong to without re-enrolling. Same rationale as
 * {@link com.lumen.common.sso.TicketMapper}.</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SysUserMfaMapper extends BaseMapper<SysUserMfa> {
}