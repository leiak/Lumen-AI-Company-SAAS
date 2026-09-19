package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.entity.FinPayment;
import com.lumen.finance.service.PaymentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/fin/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinPayment> create(@RequestBody CreatePaymentRequest req) {
        return R.ok(paymentService.create(
            req.getSourceType(), req.getSourceId(), req.getAmount(),
            req.getPayee(), req.getPaymentMethod()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinPayment> get(@PathVariable Long id) {
        return R.ok(paymentService.get(id));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinPayment>> list(@RequestParam @NotBlank String sourceType,
                                     @RequestParam @NotNull Long sourceId) {
        return R.ok(paymentService.findBySource(sourceType, sourceId));
    }

    @Data
    public static class CreatePaymentRequest {
        @NotBlank
        private String sourceType;
        @NotNull
        private Long sourceId;
        @NotNull
        @Positive
        private BigDecimal amount;
        private String payee;
        @NotBlank
        private String paymentMethod;
    }
}
