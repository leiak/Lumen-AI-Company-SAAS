package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.dto.GenerateBankFileRequest;
import com.lumen.payroll.entity.PayBankFile;
import com.lumen.payroll.service.BankFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay/bank-file")
@RequiredArgsConstructor
public class BankFileController {

    private final BankFileService bankFileService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayBankFile> generate(@RequestBody @Valid GenerateBankFileRequest req) {
        return R.ok(bankFileService.generate(req));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayBankFile> send(@PathVariable Long id) {
        return R.ok(bankFileService.send(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayBankFile> confirm(@PathVariable Long id) {
        return R.ok(bankFileService.markConfirmed(id));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PayBankFile>> listByPeriod(@RequestParam String period) {
        return R.ok(bankFileService.listByPeriod(period));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayBankFile> get(@PathVariable Long id) {
        return R.ok(bankFileService.get(id));
    }
}