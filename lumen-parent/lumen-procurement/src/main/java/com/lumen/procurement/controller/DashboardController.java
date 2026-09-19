package com.lumen.procurement.controller;

import com.lumen.common.core.domain.R;
import com.lumen.procurement.service.ProcProcurementDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/proc/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProcProcurementDashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.stats());
    }
}