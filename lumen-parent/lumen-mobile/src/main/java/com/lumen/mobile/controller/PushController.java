package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import com.lumen.mobile.dto.PushRequest;
import com.lumen.mobile.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 推送触发接口（admin / 业务系统调用）。
 */
@RestController
@RequestMapping("/mobile/admin/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @PostMapping("/user")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<Integer> pushToUser(@RequestBody @Valid PushRequest req) {
        return R.ok(pushService.pushToUser(req.getUserId(), req.getTitle(),
            req.getContent(), req.getDeepLink()));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<Integer> broadcast(@RequestParam String platform,
                                 @RequestParam String title,
                                 @RequestParam String content) {
        return R.ok(pushService.pushToAll(platform, title, content));
    }
}
