package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.entity.FinPeriod;
import com.lumen.finance.service.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fin/period")
@RequiredArgsConstructor
public class PeriodController {

    private final PeriodService periodService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinPeriod>> list() {
        return R.ok(periodService.list());
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPeriod> save(@RequestBody FinPeriod req) {
        return R.ok(periodService.save(req));
    }

    @PostMapping("/{period}/close")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPeriod> close(@PathVariable String period) {
        return R.ok(periodService.close(period));
    }

    @PostMapping("/{period}/lock")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPeriod> lock(@PathVariable String period) {
        return R.ok(periodService.lock(period));
    }
}
