package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.redis.utils.RedisUtils;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtProperties;
import com.lumen.common.security.jwt.JwtTokenProvider;
import com.lumen.common.sso.TicketManager;
import com.lumen.common.sso.TicketManager.TicketPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SSO 票据签发与兑换服务。
 * <p>
 * 流程：
 * <ol>
 *   <li>已登录用户调用 {@code /sso/issue} → 写入票据，返回不透明字符串。</li>
 *   <li>下游应用拿到票据后调用 {@code /sso/exchange} → 原子消费票据并换取 JWT。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    /** Redis 中在线 session 缓存前缀，与 {@code SessionService} 对齐。 */
    private static final String REDIS_SESSION_KEY_PREFIX = "auth:session:";
    /** Redis session 缓存 TTL：与 JWT access token 有效期一致（2h）。 */
    private static final long REDIS_SESSION_TTL_SECONDS = 2L * 60 * 60;

    private final TicketManager ticketManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SysUserMapper userMapper;
    private final RedisUtils redisUtils;

    /**
     * 签发 SSO 票据。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param appId    目标应用标识（{@code null} 时使用默认应用）
     * @return 票据与到期时间的封装
     */
    public R<IssueResult> issue(Long userId, Long tenantId, String appId) {
        if (userId == null || userId <= 0) {
            return R.fail(CommonConstants.FAIL_CODE, "userId 非法");
        }
        if (tenantId == null || tenantId < 0) {
            return R.fail(CommonConstants.FAIL_CODE, "tenantId 非法");
        }
        String effectiveAppId = (appId == null || appId.isBlank()) ? "lumen-default" : appId;

        String ticket = ticketManager.issue(userId, tenantId, effectiveAppId, null);
        return R.ok(new IssueResult(ticket, LocalDateTime.now().plusMinutes(5)));
    }

    /**
     * 兑换 JWT：消费票据后写入 Redis session 并签发 access token。
     *
     * @param ticket 票据
     * @param appId  应用标识（必须与签发时一致）
     * @return 兑换结果（JWT + sessionId + 用户/租户信息）
     */
    public R<ExchangeResult> exchange(String ticket, String appId) {
        if (ticket == null || ticket.isBlank()) {
            throw new ServiceException(400, "ticket 不能为空");
        }
        if (appId == null || appId.isBlank()) {
            throw new ServiceException(400, "appId 不能为空");
        }

        TicketPrincipal principal = ticketManager.consume(ticket, appId);
        if (principal == null) {
            // 双花 / 过期 / appId 不匹配 / 不存在 都走这条分支。
            throw new ServiceException(CommonConstants.UNAUTHORIZED, "Ticket invalid or expired");
        }

        // 兑换时再加载一次 user 信息，确保 userName/nickName 是最新值；
        // userId 才是 JWT 的权威来源，user 记录找不到时仍允许兑换（仅昵称留空）。
        SysUser user = userMapper.findByUserId(principal.userId());
        String userName = user != null ? user.getUserName() : null;
        String nickName = user != null ? user.getNickName() : null;

        String sessionId = UUID.randomUUID().toString();
        UserContext ctx = UserContext.builder()
            .userId(principal.userId())
            .tenantId(principal.tenantId())
            .userName(userName)
            .nickName(nickName)
            .tokenId(sessionId)
            .build();
        String accessToken = jwtTokenProvider.generateAccessToken(ctx);

        // 与 LoginService/SessionService 保持一致：在线 session 写入 Redis。
        redisUtils.setSeconds(REDIS_SESSION_KEY_PREFIX + sessionId,
            principal.userId() + ":" + principal.tenantId(),
            REDIS_SESSION_TTL_SECONDS);

        log.info("SSO ticket exchanged: userId={}, tenantId={}, appId={}, sessionId={}",
            principal.userId(), principal.tenantId(), principal.appId(), sessionId);

        ExchangeResult result = new ExchangeResult(
            accessToken,
            sessionId,
            jwtProperties.getAccessExpireSeconds(),
            principal.userId(),
            principal.tenantId(),
            userName,
            nickName);
        return R.ok(result);
    }

    /**
     * 票据签发结果。
     */
    public record IssueResult(String ticket, LocalDateTime expiresAt) {
    }

    /** 票据兑换结果。 */
    public record ExchangeResult(
        String token,
        String sessionId,
        long expiresInSeconds,
        Long userId,
        Long tenantId,
        String userName,
        String nickName) {
    }
}