package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.LoginRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 移动端 Auth 入口（mobile JWT 短 TTL 4h — application.yml lumen.security.jwt.mobile-ttl-seconds=14400）。
 * 短信验证码: TODO P5 接 SMS 服务。
 */
@Slf4j
@RestController
@RequestMapping("/mobile/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * 手机号 + 短信验证码登录。返回占位 JWT + 用户信息（P5 接入真实 SMS）。
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody @Valid LoginRequest req) {
        log.info("Mobile login attempt phone={}", req.getPhone());
        // TODO P5: 校验短信码（Redis ttl），查 sys_user.user，按 mobile-ttl-seconds=14400 (4h) 签发 JWT
        Map<String, Object> data = new HashMap<>();
        data.put("phone", req.getPhone());
        data.put("token", "MOBILE_JWT_PLACEHOLDER");
        data.put("ttlSeconds", 14400);
        data.put("userId", 0L);
        return R.ok(data);
    }

    /**
     * 当前用户 profile — 从 ctx 直接读。
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            // 调用方应已 isAuthenticated — 此处兜底返回 401
            return R.fail(401, "no user context");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userId", ctx.getUserId());
        data.put("userName", ctx.getUserName());
        data.put("nickName", ctx.getNickName());
        data.put("tenantId", ctx.getTenantId());
        data.put("deptId", ctx.getDeptId());
        data.put("roles", ctx.getRoles());
        return R.ok(data);
    }
}
