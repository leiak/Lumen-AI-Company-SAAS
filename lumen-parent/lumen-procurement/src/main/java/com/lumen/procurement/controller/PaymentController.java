package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.ApplyPaymentRequest;
import com.lumen.procurement.entity.ProcPayment;
import com.lumen.procurement.service.ProcPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proc/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final ProcPaymentService paymentService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcPayment>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String status) {
        return R.ok(paymentService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcPayment> get(@PathVariable Long id) {
        return R.ok(paymentService.getById(id));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcPayment> apply(@RequestBody @Valid ApplyPaymentRequest req) {
        return R.ok(paymentService.apply(req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcPayment> approve(@PathVariable Long id) {
        return R.ok(paymentService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcPayment> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(paymentService.reject(id, reason));
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcPayment> markPaid(@PathVariable Long id) {
        return R.ok(paymentService.markPaid(id));
    }

    @GetMapping("/by-source")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<ProcPayment>> bySource(@RequestParam String sourceType,
                                           @RequestParam Long sourceId) {
        return R.ok(paymentService.findBySource(sourceType, sourceId));
    }
}