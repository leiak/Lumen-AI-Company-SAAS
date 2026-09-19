package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.ReverseVoucherRequest;
import com.lumen.finance.dto.SaveVoucherRequest;
import com.lumen.finance.entity.FinVoucher;
import com.lumen.finance.entity.FinVoucherEntry;
import com.lumen.finance.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fin/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinVoucher> save(@RequestBody @Valid SaveVoucherRequest req) {
        return R.ok(voucherService.save(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinVoucher> get(@PathVariable Long id) {
        return R.ok(voucherService.get(id));
    }

    @GetMapping("/{id}/entries")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinVoucherEntry>> entries(@PathVariable Long id) {
        return R.ok(voucherService.entries(id));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinVoucher> post(@PathVariable Long id) {
        return R.ok(voucherService.post(id));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinVoucher> reverse(@PathVariable Long id,
                                 @RequestBody @Valid ReverseVoucherRequest req) {
        return R.ok(voucherService.reverse(id, req.getReason()));
    }
}
