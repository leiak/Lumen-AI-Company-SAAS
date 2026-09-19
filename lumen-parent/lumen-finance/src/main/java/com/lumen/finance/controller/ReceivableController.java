package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.CollectRequest;
import com.lumen.finance.entity.FinReceivable;
import com.lumen.finance.service.ReceivableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fin/receivable")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService receivableService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinReceivable>> list(@RequestParam Long customerId,
                                       @RequestParam(required = false, defaultValue = "pending") String status) {
        return R.ok(receivableService.listByCustomerAndStatus(customerId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinReceivable> get(@PathVariable Long id) {
        return R.ok(receivableService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinReceivable> save(@RequestBody FinReceivable req) {
        return R.ok(receivableService.save(req));
    }

    @PostMapping("/{id}/collect")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinReceivable> collect(@PathVariable Long id,
                                    @RequestBody @Valid CollectRequest req) {
        return R.ok(receivableService.collect(id, req.getAmount()));
    }
}
