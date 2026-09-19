package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.SaveInvoiceRequest;
import com.lumen.finance.entity.FinInvoice;
import com.lumen.finance.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/fin/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinInvoice> save(@RequestBody @Valid SaveInvoiceRequest req) {
        return R.ok(invoiceService.save(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinInvoice> get(@PathVariable Long id) {
        return R.ok(invoiceService.get(id));
    }

    @PostMapping("/{id}/recognize")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinInvoice> recognize(@PathVariable Long id) {
        return R.ok(invoiceService.recognize(id));
    }

    @PostMapping("/{id}/certify")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinInvoice> certify(@PathVariable Long id) {
        return R.ok(invoiceService.certify(id));
    }

    /**
     * 安全要求 #9: 仅 finance_admin / super_admin 能看敏感字段原文。
     */
    @GetMapping("/{id}/sensitive-info")
    @PreAuthorize("hasAnyRole('super_admin','finance_admin')")
    public R<Map<String, String>> sensitiveInfo(@PathVariable Long id) {
        return R.ok(invoiceService.sensitiveInfo(id));
    }
}
