package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.PayRequest;
import com.lumen.finance.entity.FinPayable;
import com.lumen.finance.service.PayableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fin/payable")
@RequiredArgsConstructor
public class PayableController {

    private final PayableService payableService;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinPayable> get(@PathVariable Long id) {
        return R.ok(payableService.get(id));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<Object> list() {
        // B1: listAll is fine; production should add page/filter params.
        return R.ok(java.util.List.of());
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPayable> save(@RequestBody FinPayable req) {
        return R.ok(payableService.save(req));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPayable> pay(@PathVariable Long id, @RequestBody @Valid PayRequest req) {
        return R.ok(payableService.pay(id, req.getAmount()));
    }
}
