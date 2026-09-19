package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.RegisterPushTokenRequest;
import com.lumen.mobile.entity.MobPushToken;
import com.lumen.mobile.service.PushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推送 Token 注册 / 注销 / 列表（用户登录后调用）。
 */
@RestController
@RequestMapping("/mobile/api/push-token")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public R<MobPushToken> register(@RequestBody @Valid RegisterPushTokenRequest req) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "no user");
        return R.ok(pushTokenService.register(uid, req.getPlatform(), req.getToken(),
            req.getDeviceId(), req.getAppVersion(), req.getDeviceModel(), req.getOsVersion()));
    }

    @PostMapping("/unregister")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> unregister(@RequestParam String platform, @RequestParam String token) {
        return R.ok(pushTokenService.unregister(platform, token));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<MobPushToken>> list() {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "no user");
        return R.ok(pushTokenService.listByUser(uid));
    }
}
