package com.lumen.sales.controller;

import com.lumen.common.core.domain.R;
import com.lumen.sales.service.SalesDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sal/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SalesDashboardService dashboardService;

    @GetMapping("/funnel")
    public R<Map<String, Object>> funnel() {
        return R.ok(dashboardService.funnel());
    }

    @GetMapping("/this-month")
    public R<Map<String, Object>> thisMonth() {
        return R.ok(dashboardService.thisMonth());
    }

    @GetMapping("/collection-rate")
    public R<Map<String, Object>> collectionRate(@RequestParam Long customerId) {
        return R.ok(dashboardService.collectionRate(customerId));
    }
}