package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.dto.RecordPaymentRequest;
import com.lumen.sales.entity.PaymentRecord;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.service.ReceivableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sal/receivable")
@RequiredArgsConstructor
@Validated
public class ReceivableController {

    private final ReceivableService receivableService;

    @GetMapping("/list")
    public R<IPage<Receivable>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long customerId) {
        return R.ok(receivableService.page(pageNum, pageSize, status, customerId));
    }

    @GetMapping("/{id}")
    public R<Receivable> get(@PathVariable Long id) {
        return R.ok(receivableService.get(id));
    }

    @GetMapping("/{id}/payments")
    public R<List<PaymentRecord>> payments(@PathVariable Long id) {
        return R.ok(receivableService.findPayments(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','finance')")
    public R<Receivable> save(@RequestBody Receivable req) {
        return R.ok(receivableService.save(req));
    }

    @PostMapping("/{id}/record-payment")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','finance')")
    public R<PaymentRecord> recordPayment(@PathVariable Long id,
                                          @RequestBody @Valid RecordPaymentRequest req) {
        return R.ok(receivableService.recordPayment(id, req.getAmount(), req.getPaymentMethod(), req.getRemark()));
    }

    @GetMapping("/overdue")
    public R<List<Receivable>> overdue() {
        return R.ok(receivableService.findOverdueNow());
    }
}