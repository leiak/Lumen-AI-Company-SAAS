package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/fin/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<Map<String, Object>> balanceSheet(@RequestParam String period) {
        return R.ok(reportService.balanceSheet(period));
    }

    @GetMapping("/income-statement")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<Map<String, Object>> incomeStatement(@RequestParam String periodFrom,
                                                  @RequestParam String periodTo) {
        return R.ok(reportService.incomeStatement(periodFrom, periodTo));
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<Map<String, Object>> cashFlow(@RequestParam String periodFrom,
                                            @RequestParam String periodTo) {
        return R.ok(reportService.cashFlow(periodFrom, periodTo));
    }
}
