package com.lumen.auth.controller;

import com.lumen.auth.dto.SsoExchangeRequest;
import com.lumen.auth.dto.SsoIssueRequest;
import com.lumen.auth.service.SsoService;
import com.lumen.auth.service.SsoService.ExchangeResult;
import com.lumen.auth.service.SsoService.IssueResult;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.core.domain.R;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 票据端点：提供票据签发与兑奖两个接口。
 * <p>
 * - {@code POST /sso/issue}：已登录用户调用，颁发不透明随机票据。身份仅来自
 *   {@link UserContextHolder}；body 中绝不接受 {@code userId}/{@code tenantId}，
 *   以避免越权为其他用户/租户签发票据。仅 {@code appId} 可由调用方指定。
 * - {@code POST /sso/exchange}：下游应用调用，原子消费票据换取 JWT。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoService ssoService;

    /**
     * 签发 SSO 票据。
     */
    @PostMapping("/issue")
    @PreAuthorize("isAuthenticated()")
    public R<IssueResult> issue(@Valid @RequestBody(required = false) SsoIssueRequest req) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ServiceException(401, "Not authenticated");
        }
        String appId = req == null ? null : req.getAppId();
        log.info("SSO issue requested: userId={}, tenantId={}, appId={}",
            ctx.getUserId(), ctx.getTenantId(), appId);
        // 身份仅来自会话上下文——永远不要从 body 读取 userId/tenantId。
        return ssoService.issue(ctx.getUserId(), ctx.getTenantId(), appId);
    }

    /**
     * 兑换 SSO 票据为 JWT。
     */
    @PostMapping("/exchange")
    public R<ExchangeResult> exchange(@Valid @RequestBody SsoExchangeRequest req,
                                      HttpServletRequest http) {
        log.info("SSO exchange requested: appId={}", req.getAppId());
        return ssoService.exchange(req.getTicket(), req.getAppId(),
            clientIp(http), http.getHeader("User-Agent"));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}