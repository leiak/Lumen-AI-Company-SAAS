package com.lumen.auth.controller;

import com.lumen.common.core.domain.R;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/user")
public class UserController {

    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            return R.fail(401, "Not authenticated");
        }
        return R.ok(Map.of(
            "userId", ctx.getUserId(),
            "tenantId", ctx.getTenantId(),
            "sessionId", ctx.getTokenId()
        ));
    }
}