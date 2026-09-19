package com.lumen.assets.controller;

import com.lumen.assets.service.ReportService;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/assets/report")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/category-summary")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<Map<String, Object>>> categorySummary() {
        return R.ok(reportService.categorySummary());
    }

    @GetMapping("/dept-summary")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<Map<String, Object>>> deptSummary() {
        return R.ok(reportService.deptSummary());
    }

    @GetMapping("/depreciation-summary")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<Map<String, Object>> depreciationSummary(@RequestParam String period) {
        return R.ok(reportService.depreciationSummary(period));
    }
}