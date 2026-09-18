package com.lumen.auth.controller;

import com.lumen.auth.dto.LoginRequest;
import com.lumen.auth.dto.LoginResult;
import com.lumen.auth.dto.MfaChallenge;
import com.lumen.auth.service.LoginService;
import com.lumen.auth.service.SessionService;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.common.security.jwt.JwtProperties;
import com.lumen.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final LoginService loginService;
    private final SessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * Password login. Returns either a {@link LoginResult} (full JWT issued) or an
     * {@link MfaChallenge} (the caller must complete {@code POST /mfa/verify}).
     */
    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        Object result = loginService.login(req, http);
        return R.ok(result);
    }

    /**
     * Exchange a refresh token for a fresh access token. The refresh token itself is unchanged.
     */
    @PostMapping("/refresh")
    public R<LoginResult> refresh(@RequestParam String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(refreshToken);
        } catch (Exception e) {
            throw new ServiceException(401, "Invalid refresh token");
        }
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new ServiceException(401, "Token is not a refresh token");
        }
        Long uid = claims.get("uid", Long.class);
        Long tid = claims.get("tid", Long.class);
        String sessionId = claims.getId();

        var session = sessionService.findBySessionId(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != 1) {
            throw new ServiceException(401, "Session expired");
        }

        UserContext accessCtx = UserContext.builder()
            .userId(uid).tenantId(tid)
            .userName(claims.get("uname", String.class))
            .nickName(claims.get("nname", String.class))
            .tokenId(sessionId)
            .build();
        String newAccess = jwtTokenProvider.generateAccessToken(accessCtx);

        return R.ok(new LoginResult(newAccess, refreshToken,
            jwtProperties.getAccessExpireSeconds(),
            sessionId, uid, tid, null, null));
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest http) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ServiceException(401, "Not authenticated");
        }
        loginService.logout(ctx.getUserId(), ctx.getTenantId(), ctx.getTokenId(), http);
        UserContextHolder.clear();
        return R.ok();
    }

    @GetMapping("/health")
    public R<String> health() {
        return R.ok("auth-service is UP");
    }
}