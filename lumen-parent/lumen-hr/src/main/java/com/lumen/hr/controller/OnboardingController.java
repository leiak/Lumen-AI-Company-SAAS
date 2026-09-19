package com.lumen.hr.controller;

import com.lumen.common.core.domain.R;
import com.lumen.hr.entity.HrOnboarding;
import com.lumen.hr.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hr/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrOnboarding> start(@RequestParam Long employeeId) {
        return R.ok(onboardingService.start(employeeId));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrOnboarding> complete(@PathVariable Long id) {
        return R.ok(onboardingService.complete(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<HrOnboarding> get(@PathVariable Long id) {
        return R.ok(onboardingService.getById(id));
    }
}
