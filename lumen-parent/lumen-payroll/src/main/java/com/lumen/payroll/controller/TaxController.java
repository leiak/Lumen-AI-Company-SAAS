package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.entity.PayTax;
import com.lumen.payroll.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pay/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PayTax>> list(@RequestParam String period) {
        return R.ok(taxService.listByPeriod(period));
    }

    @GetMapping("/by-employee")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayTax> byEmployee(@RequestParam Long employeeId, @RequestParam String period) {
        return R.ok(taxService.findByEmployeeAndPeriod(employeeId, period));
    }

    @GetMapping("/by-slip/{slipId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayTax> bySlip(@PathVariable Long slipId) {
        return R.ok(taxService.findBySlip(slipId));
    }
}