package com.lumen.hr.controller;

import com.lumen.common.core.domain.R;
import com.lumen.hr.dto.StartCycleRequest;
import com.lumen.hr.dto.SubmitPerformanceRequest;
import com.lumen.hr.entity.HrPerformanceCycle;
import com.lumen.hr.entity.HrPerformanceScore;
import com.lumen.hr.service.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hr/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping("/cycle/start")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrPerformanceCycle> startCycle(@RequestBody @Valid StartCycleRequest req) {
        return R.ok(performanceService.startCycle(req));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrPerformanceScore> submit(@RequestBody @Valid SubmitPerformanceRequest req) {
        return R.ok(performanceService.submitScore(req));
    }

    @GetMapping("/score")
    @PreAuthorize("isAuthenticated()")
    public R<List<HrPerformanceScore>> listScore(@RequestParam Long cycleId,
                                                  @RequestParam Long employeeId) {
        return R.ok(performanceService.listByCycleAndEmployee(cycleId, employeeId));
    }
}
