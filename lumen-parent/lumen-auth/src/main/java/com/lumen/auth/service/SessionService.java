package com.lumen.auth.service;

import com.lumen.auth.entity.SysUserSession;
import com.lumen.auth.mapper.SysUserSessionMapper;
import com.lumen.common.redis.utils.RedisUtils;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manages user sessions: persists to MySQL (sys_user_session) + caches active state in Redis.
 * Redis key format: auth:session:{sessionId} → "userId:tenantId" (TTL = refresh TTL).
 *
 * The sessionId is also used as the JWT jti (see JwtTokenProvider.generateToken) so the
 * session row can be cross-referenced from a presented token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String REDIS_SESSION_KEY_PREFIX = "auth:session:";
    /** Cache TTL aligns with refresh-token TTL (14d) so online-user lookup stays accurate. */
    private static final long REDIS_SESSION_TTL_SECONDS = 14L * 24 * 60 * 60;

    private final SysUserSessionMapper sessionMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisUtils redisUtils;

    public SysUserSession createSession(Long userId, Long tenantId, String nickName,
                                        String ip, String ua, String device) {
        String sessionId = UUID.randomUUID().toString();

        UserContext ctx = UserContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .nickName(nickName)
            .tokenId(sessionId)
            .build();
        String accessToken = jwtTokenProvider.generateAccessToken(ctx);
        String refreshToken = jwtTokenProvider.generateRefreshToken(ctx);

        SysUserSession session = new SysUserSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setRefreshToken(refreshToken);
        session.setIp(ip);
        session.setUserAgent(ua);
        session.setDevice(device);
        session.setLoginAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpireAt(LocalDateTime.now().plusSeconds(REDIS_SESSION_TTL_SECONDS));
        session.setStatus(1);
        session.setUpdateBy(userId);
        sessionMapper.insert(session);

        // Cache active state for fast online-user lookup
        redisUtils.setSeconds(REDIS_SESSION_KEY_PREFIX + sessionId,
            userId + ":" + tenantId, REDIS_SESSION_TTL_SECONDS);

        log.info("Session created: userId={}, tenantId={}, sessionId={}", userId, tenantId, sessionId);
        return session;
    }

    public SysUserSession findBySessionId(String sessionId) {
        return sessionMapper.findBySessionId(sessionId);
    }

    public void revoke(String sessionId) {
        SysUserSession s = findBySessionId(sessionId);
        if (s == null) return;
        s.setStatus(0);
        s.setLogoutAt(LocalDateTime.now());
        s.setUpdateBy(s.getUserId());
        sessionMapper.updateById(s);
        redisUtils.delete(REDIS_SESSION_KEY_PREFIX + sessionId);
        log.info("Session revoked: {}", sessionId);
    }
}