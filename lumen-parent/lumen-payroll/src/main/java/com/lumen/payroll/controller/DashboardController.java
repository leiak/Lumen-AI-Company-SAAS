package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.service.PayrollDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/pay/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final PayrollDashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.stats());
    }
}