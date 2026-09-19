package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 移动端消息通知代理接口。
 * P4 阶段不接 Feign；这里返回 stub + TODO P5 接入 message-center。
 */
@Slf4j
@RestController
@RequestMapping("/mobile/api/notification")
public class NotificationProxyController {

    /**
     * 通知列表。P5: Feign → message-center /msg/notification/list
     */
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Mobile notification list (stub) pageNum={} pageSize={}", pageNum, pageSize);
        return R.ok(List.of());
    }

    /**
     * 标记已读。P5: Feign → message-center /msg/notification/{id}/read
     */
    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> markRead(@PathVariable Long id) {
        log.info("Mobile mark-read (stub) id={}", id);
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("read", true);
        return R.ok(data);
    }
}
