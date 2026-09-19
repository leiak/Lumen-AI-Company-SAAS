package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.dto.CalculateSocialSecurityRequest;
import com.lumen.payroll.entity.PaySocialSecurity;
import com.lumen.payroll.service.SocialSecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay/social-security")
@RequiredArgsConstructor
public class SocialSecurityController {

    private final SocialSecurityService socialSecurityService;

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PaySocialSecurity> calculate(@RequestBody @Valid CalculateSocialSecurityRequest req) {
        return R.ok(socialSecurityService.calculate(req));
    }

    @PostMapping("/declare")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Integer> declare(@RequestParam String period) {
        return R.ok(socialSecurityService.declare(period));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySocialSecurity>> list(@RequestParam String period) {
        return R.ok(socialSecurityService.listByPeriod(period));
    }
}