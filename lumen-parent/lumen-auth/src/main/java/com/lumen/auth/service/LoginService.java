package com.lumen.auth.service;

import com.lumen.auth.dto.LoginRequest;
import com.lumen.auth.dto.LoginResult;
import com.lumen.auth.dto.MfaChallenge;
import com.lumen.auth.entity.SysAuthAudit;
import com.lumen.auth.entity.SysLoginFail;
import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserSession;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 30;
    /** MFA step-up token TTL — short enough to limit replay window if leaked. */
    private static final long MFA_TOKEN_TTL_SECONDS = 300L;

    private final SysUserMapper userMapper;
    private final SysLoginFailMapper loginFailMapper;
    private final AuthAuditRecorder authAuditRecorder;
    private final SessionService sessionService;
    private final MfaService mfaService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${lumen.auth.audit.async:true}")
    private boolean asyncAudit;

    /**
     * Authenticate user credentials. If MFA is enrolled (or required by policy) AND the
     * password is valid, returns an {@link MfaChallenge} instead of a full
     * {@link LoginResult} — the caller must complete the step-up at
     * {@code POST /mfa/verify}.
     */
    public Object login(LoginRequest req, HttpServletRequest http) {
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

        // Reset fail count on successful password verify — even if MFA is required.
        userMapper.resetFailCount(user.getUserId());

        // MFA step-up: if enrolled or admin-required, issue a short-lived mfa token
        // and return the challenge instead of a full JWT.
        boolean mfaNeeded = mfaService.isEnabled(user.getUserId())
            || (user.getMfaRequired() != null && user.getMfaRequired() == 1);
        if (mfaNeeded) {
            UserContext ctx = UserContext.builder()
                .userId(user.getUserId())
                .tenantId(user.getTenantId())
                .userName(user.getUserName())
                .nickName(user.getNickName())
                .build();
            String mfaToken = jwtTokenProvider.generateMfaToken(ctx, MFA_TOKEN_TTL_SECONDS);
            recordAudit(user.getUserId(), user.getUserName(), user.getTenantId(),
                "MFA_CHALLENGE", 1, ip, ua, "ok");
            log.info("MFA challenge issued: userId={}, tenantId={}", user.getUserId(), user.getTenantId());
            return new MfaChallenge(mfaToken, MFA_TOKEN_TTL_SECONDS);
        }

        // No MFA — proceed straight to session + JWT issuance.
        return finishLogin(user, req, ip, ua);
    }

    /**
     * Complete MFA step-up: validate the mfa token, verify the TOTP code, then issue
     * a full session+JWT. Returns the same shape as a normal {@code LoginResult}.
     */
    public LoginResult verifyMfa(String mfaToken, String code, HttpServletRequest http) {
        String ip = clientIp(http);
        String ua = http.getHeader("User-Agent");

        ClaimsView v;
        try {
            v = parseMfaToken(mfaToken);
        } catch (Exception e) {
            recordAudit(null, null, null, "MFA_VERIFY", 0, ip, ua, "bad_token");
            throw new ServiceException(401, "Invalid or expired MFA token");
        }

        Long uid = v.uid;
        Long tid = v.tid;
        String userName = v.uname;
        String nickName = v.nname;

        SysUser user = userMapper.findByUserId(uid);
        if (user == null) {
            recordAudit(uid, userName, tid, "MFA_VERIFY", 0, ip, ua, "user_missing");
            throw new ServiceException(401, "User no longer exists");
        }
        if (!mfaService.verify(uid, code)) {
            recordAudit(uid, userName, tid, "MFA_VERIFY", 0, ip, ua, "bad_code");
            throw new ServiceException(401, "Invalid TOTP code");
        }

        // Build a fresh LoginRequest-equivalent for finishLogin: tenant comes from the
        // mfa-token claims (the original login used req.tenantId).
        LoginRequest req = new LoginRequest();
        req.setTenantId(tid);
        req.setUserName(userName);
        req.setDevice("WEB");

        recordAudit(uid, userName, tid, "MFA_VERIFY", 1, ip, ua, "ok");
        return finishLogin(user, req, ip, ua);
    }

    public void logout(Long userId, Long tenantId, String sessionId, HttpServletRequest http) {
        if (sessionId != null) {
            sessionService.revoke(sessionId);
        }
        recordAudit(userId, null, tenantId, "LOGOUT", 1, clientIp(http),
            http.getHeader("User-Agent"), "ok");
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private LoginResult finishLogin(SysUser user, LoginRequest req,
                                    String ip, String ua) {
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

    private ClaimsView parseMfaToken(String token) {
        var claims = jwtTokenProvider.parseToken(token);
        String type = claims.get("type", String.class);
        Object mfaFlag = claims.get("mfa_token");
        if (!"mfa".equals(type) || !"1".equals(mfaFlag)) {
            throw new ServiceException(401, "Not an MFA token");
        }
        ClaimsView v = new ClaimsView();
        v.uid = claims.get("uid", Long.class);
        v.tid = claims.get("tid", Long.class);
        v.uname = claims.get("uname", String.class);
        v.nname = claims.get("nname", String.class);
        return v;
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
        authAuditRecorder.record(audit);
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }

    /** Tiny carrier to keep parseMfaToken tidy. */
    private static final class ClaimsView {
        Long uid;
        Long tid;
        String uname;
        String nname;
    }
}