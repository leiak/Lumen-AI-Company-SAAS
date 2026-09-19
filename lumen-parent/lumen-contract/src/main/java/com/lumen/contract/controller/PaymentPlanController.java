package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.dto.PaymentPlanDto;
import com.lumen.contract.entity.PaymentPlan;
import com.lumen.contract.service.PaymentPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;

    @PostMapping("/{id}/payment-plan")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Integer> generatePlan(@PathVariable Long id, @RequestBody @Valid List<PaymentPlanDto> plans) {
        return R.ok(paymentPlanService.generatePlan(id, plans));
    }

    @GetMapping("/payment-plan/find-overdue")
    public R<List<PaymentPlan>> findOverdue() {
        // Manual trigger — scheduled version is TODO P5 (Quartz)
        if (UserContextHolder.getTenantId() == null) {
            throw new ServiceException(401, "No tenant context");
        }
        return R.ok(paymentPlanService.findOverdue());
    }

    @PostMapping("/payment-plan/{pid}/mark-completed")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<PaymentPlan> markCompleted(@PathVariable Long pid,
                                          @RequestBody Map<String, Object> body) {
        BigDecimal actualAmount = body.get("actualAmount") == null ? null
            : new BigDecimal(String.valueOf(body.get("actualAmount")));
        LocalDate actualDate = body.get("actualDate") == null ? null
            : LocalDate.parse(String.valueOf(body.get("actualDate")));
        return R.ok(paymentPlanService.markCompleted(pid, actualAmount, actualDate));
    }
}
