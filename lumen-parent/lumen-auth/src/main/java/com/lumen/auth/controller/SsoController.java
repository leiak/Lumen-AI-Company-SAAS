package com.lumen.auth.controller;

import com.lumen.auth.service.SsoService;
import com.lumen.auth.service.SsoService.ExchangeResult;
import com.lumen.auth.service.SsoService.IssueResult;
import com.lumen.common.core.domain.R;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SSO 票据端点：提供票据签发与兑奖两个接口。
 * <p>
 * - {@code POST /sso/issue}：已登录用户调用，颁发不透明随机票据。
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
     * <p>
     * 入参优先级：body 显式参数 &gt; 调用方当前会话上下文（UserContextHolder）。
     * </p>
     */
    @PostMapping("/issue")
    public R<IssueResult> issue(@RequestBody(required = false) Map<String, Object> body) {
        Long userId = readLong(body, "userId", UserContextHolder.getUserId());
        Long tenantId = readLong(body, "tenantId", UserContextHolder.getTenantId());
        String appId = readString(body, "appId");
        log.info("SSO issue requested: userId={}, tenantId={}, appId={}", userId, tenantId, appId);
        return ssoService.issue(userId, tenantId, appId);
    }

    /**
     * 兑换 SSO 票据为 JWT。
     */
    @PostMapping("/exchange")
    public R<ExchangeResult> exchange(@RequestBody Map<String, Object> body) {
        String ticket = body == null ? null : asString(body.get("ticket"));
        String appId = body == null ? null : asString(body.get("appId"));
        log.info("SSO exchange requested: appId={}", appId);
        return ssoService.exchange(ticket, appId);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Long readLong(Map<String, Object> body, String key, Long fallback) {
        if (body == null) return fallback;
        Object v = body.get(key);
        if (v == null) return fallback;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String readString(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : asString(v);
    }

    private static String asString(Object v) {
        if (v == null) return null;
        return v.toString();
    }
}