package com.lumen.hr.controller;

import com.lumen.common.core.domain.R;
import com.lumen.hr.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/hr/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Long>> stats() {
        return R.ok(dashboardService.stats());
    }
}
