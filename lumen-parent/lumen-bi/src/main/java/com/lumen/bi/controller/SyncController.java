package com.lumen.bi.controller;

import com.lumen.bi.service.SyncService;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据同步接口。安全要求 #16: 仅 admin 触发 ETL。
 */
@RestController
@RequestMapping("/bi/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /**
     * 安全要求 #16: 触发同步 — 仅 admin。
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Map<String, Object>> trigger(@RequestParam String metricCode) {
        return R.ok(syncService.triggerSync(metricCode));
    }

    @GetMapping("/{metricCode}/status")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> status(@PathVariable String metricCode) {
        return R.ok(syncService.getSyncStatus(metricCode));
    }
}