package com.lumen.inventory.controller;

import com.lumen.common.core.domain.R;
import com.lumen.inventory.service.InventoryDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/inv/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final InventoryDashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.stats());
    }
}