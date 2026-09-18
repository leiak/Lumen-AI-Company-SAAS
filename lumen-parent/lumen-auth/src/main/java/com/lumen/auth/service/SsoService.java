package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserSession;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtProperties;
import com.lumen.common.security.jwt.JwtTokenProvider;
import com.lumen.common.sso.TicketConstants;
import com.lumen.common.sso.TicketManager;
import com.lumen.common.sso.TicketManager.TicketPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * SSO 票据签发与兑换服务。
 * <p>
 * 流程：
 * <ol>
 *   <li>已登录用户调用 {@code /sso/issue} → 写入票据，返回不透明字符串。</li>
 *   <li>下游应用拿到票据后调用 {@code /sso/exchange} → 原子消费票据并换取 JWT，
 *       同时复用 {@code SessionService.createSession} 写入 {@code sys_user_session} 与 Redis 缓存，
 *       与 {@code LoginService.login} 保持一致。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    /** SSO 兑换出的 session 在 sys_user_session 中的设备标记。 */
    private static final String SSO_DEVICE = "SSO";

    private final TicketManager ticketManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SysUserMapper userMapper;
    private final SessionService sessionService;

    /**
     * 签发 SSO 票据。
     * <p>
     * 身份 ({@code userId}/{@code tenantId}) 校验由 {@link TicketManager} 完成；本方法
     * 不再做重复校验——见 CRITICAL C2/M9 的整改说明。
     * </p>
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param appId    目标应用标识（{@code null} 或空时回退到 {@link TicketConstants#DEFAULT_APP_ID}）
     * @return 票据与到期时间的封装
     */
    public R<IssueResult> issue(Long userId, Long tenantId, String appId) {
        try {
            String effectiveAppId = (appId == null || appId.isBlank())
                ? TicketConstants.DEFAULT_APP_ID : appId;

            String ticket = ticketManager.issue(userId, tenantId, effectiveAppId, null);
            return R.ok(new IssueResult(ticket, LocalDateTime.now().plus(TicketConstants.DEFAULT_TTL)));
        } catch (IllegalArgumentException ex) {
            // 把 manager 的参数校验错误翻译成标准的 fail 响应。
            return R.fail(CommonConstants.FAIL_CODE, ex.getMessage());
        }
    }

    /**
     * 兑换 JWT：消费票据后写入 {@code sys_user_session}（复用 {@link SessionService}）、
     * Redis 在线缓存并签发 access token。
     *
     * @param ticket 票据
     * @param appId  应用标识（必须与签发时一致）
     * @param ip     客户端 IP（用于 session 记录）
     * @param ua     User-Agent（用于 session 记录）
     * @return 兑换结果（JWT + sessionId + 用户/租户信息）
     */
    public R<ExchangeResult> exchange(String ticket, String appId, String ip, String ua) {
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

        // 兑换时再加载一次 user 信息，确保 userName/nickName/deptId/dataScope 是最新值；
        // userId 才是 JWT 的权威来源，user 记录找不到时仍允许兑换（仅昵称留空）。
        SysUser user = userMapper.findByUserId(principal.userId());
        String userName = user != null ? user.getUserName() : null;
        String nickName = user != null ? user.getNickName() : null;
        Long deptId = user != null ? user.getDeptId() : null;
        Integer dataScope = user != null ? user.getDataScope() : null;

        // 复用 SessionService：写 sys_user_session + Redis 在线缓存，与 LoginService 对齐。
        // refresh_token 在 sessionService.createSession 内部生成，sessionId 即其返回值。
        SysUserSession session = sessionService.createSession(
            principal.userId(), principal.tenantId(), nickName, ip, ua, SSO_DEVICE);
        String sessionId = session.getSessionId();

        // 用与 LoginService 一致的字段全集（含 deptId/dataScope）构造 UserContext，
        // 保证 JWT 解析后的 UserContextHolder 与直接登录一致。
        UserContext ctx = UserContext.builder()
            .userId(principal.userId())
            .tenantId(principal.tenantId())
            .userName(userName)
            .nickName(nickName)
            .deptId(deptId)
            .dataScope(dataScope)
            .tokenId(sessionId)
            .build();
        String accessToken = jwtTokenProvider.generateAccessToken(ctx);

        long expiresInSeconds = jwtProperties.getAccessExpireSeconds();
        log.info("SSO ticket exchanged: userId={}, tenantId={}, appId={}, sessionId={}",
            principal.userId(), principal.tenantId(), principal.appId(), sessionId);

        ExchangeResult result = new ExchangeResult(
            accessToken,
            sessionId,
            expiresInSeconds,
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