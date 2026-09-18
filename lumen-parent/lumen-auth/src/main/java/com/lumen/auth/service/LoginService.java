package com.lumen.auth.service;

import com.lumen.auth.dto.LoginRequest;
import com.lumen.auth.dto.LoginResult;
import com.lumen.auth.entity.SysAuthAudit;
import com.lumen.auth.entity.SysLoginFail;
import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserSession;
import com.lumen.auth.mapper.SysAuthAuditMapper;
import com.lumen.auth.mapper.SysLoginFailMapper;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtProperties;
import com.lumen.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 30;

    private final SysUserMapper userMapper;
    private final SysLoginFailMapper loginFailMapper;
    private final SysAuthAuditMapper authAuditMapper;
    private final SessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${lumen.auth.audit.async:true}")
    private boolean asyncAudit;

    public LoginResult login(LoginRequest req, HttpServletRequest http) {
        String ip = clientIp(http);
        String ua = http.getHeader("User-Agent");

        SysUser user = userMapper.findByTenantAndUsername(req.getTenantId(), req.getUserName());
        if (user == null) {
            recordFail(req, ip, ua, "user_not_found");
            throw new ServiceException(401, "Invalid credentials");
        }

        // Check lock
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            recordFail(req, ip, ua, "locked");
            throw new ServiceException(423, "Account locked until " + user.getLockUntil());
        }

        // Check status
        if (!"0".equals(user.getStatus())) {
            recordFail(req, ip, ua, "disabled");
            throw new ServiceException(403, "Account disabled");
        }

        // MFA placeholder — full MFA enforcement lands in P2
        if (user.getMfaEnabled() != null && user.getMfaEnabled() == 1) {
            log.info("User {} has MFA enabled but MFA verification not implemented yet (P2)",
                user.getUserId());
        }

        // Password
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            userMapper.incrFailCount(user.getUserId());
            int newFailCount = user.getFailCount() + 1;
            if (newFailCount >= MAX_FAIL_COUNT) {
                userMapper.lockUntil(user.getUserId(), LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            }
            recordFail(req, ip, ua, "bad_password(" + newFailCount + ")");
            throw new ServiceException(401, "Invalid credentials");
        }

        // Success
        userMapper.resetFailCount(user.getUserId());
        SysUserSession session = sessionService.createSession(
            user.getUserId(), user.getTenantId(), user.getNickName(),
            ip, ua, req.getDevice());

        recordAudit(user.getUserId(), user.getUserName(), user.getTenantId(),
            "LOGIN", 1, ip, ua, "ok");

        // Build an access token for the response (session_id = JWT jti is bound in createSession)
        UserContext accessCtx = UserContext.builder()
            .userId(user.getUserId())
            .tenantId(user.getTenantId())
            .userName(user.getUserName())
            .nickName(user.getNickName())
            .tokenId(session.getSessionId())
            .build();
        String accessToken = jwtTokenProvider.generateAccessToken(accessCtx);

        return new LoginResult(
            accessToken,
            session.getRefreshToken(),
            jwtProperties.getAccessExpireSeconds(),
            session.getSessionId(),
            user.getUserId(), user.getTenantId(), user.getUserName(), user.getNickName());
    }

    public void logout(Long userId, Long tenantId, String sessionId, HttpServletRequest http) {
        if (sessionId != null) {
            sessionService.revoke(sessionId);
        }
        recordAudit(userId, null, tenantId, "LOGOUT", 1, clientIp(http),
            http.getHeader("User-Agent"), "ok");
    }

    private void recordFail(LoginRequest req, String ip, String ua, String reason) {
        SysLoginFail fail = new SysLoginFail();
        fail.setUserName(req.getUserName());
        fail.setTenantId(req.getTenantId());
        fail.setIp(ip);
        fail.setUserAgent(ua);
        fail.setFailReason(reason);
        loginFailMapper.insert(fail);
    }

    private void recordAudit(Long userId, String userName, Long tenantId,
                             String action, int status, String ip, String ua, String detail) {
        SysAuthAudit audit = new SysAuthAudit();
        audit.setUserId(userId);
        audit.setUserName(userName);
        audit.setTenantId(tenantId);
        audit.setAction(action);
        audit.setStatus(status);
        audit.setIp(ip);
        audit.setUserAgent(ua);
        audit.setDetail(detail);
        audit.setAuditAt(LocalDateTime.now());
        if (asyncAudit) {
            auditAsync(audit);
        } else {
            authAuditMapper.insert(audit);
        }
    }

    @Async
    public void auditAsync(SysAuthAudit audit) {
        try {
            authAuditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("audit insert failed: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}