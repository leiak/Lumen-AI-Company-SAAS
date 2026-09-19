package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.dto.PaymentPlanDto;
import com.lumen.contract.entity.PaymentPlan;
import com.lumen.contract.mapper.PaymentPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收款/付款计划管理。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>planned_amount 在创建后不可变(只允许 actual_amount 更新)。</li>
 *   <li>actual_amount 必须 <= planned_amount,否则 400。</li>
 *   <li>findOverdue 由 Quartz 定时任务调用 — TODO P5。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_OVERDUE = "overdue";

    private final PaymentPlanMapper paymentPlanMapper;
    private final ContractService contractService;

    /** Generate payment plans for a contract from a list of DTOs. */
    @Transactional
    public int generatePlan(Long contractId, List<PaymentPlanDto> dtos) {
        UserContext ctx = requireContext();
        if (dtos == null || dtos.isEmpty()) {
            throw new ServiceException(400, "plans must not be empty");
        }
        List<PaymentPlan> plans = new ArrayList<>();
        int i = 0;
        for (PaymentPlanDto dto : dtos) {
            if (dto.getPlannedAmount() == null || dto.getPlannedAmount().signum() <= 0) {
                throw new ServiceException(400, "plannedAmount must be positive (index=" + i + ")");
            }
            if (dto.getPlannedDate() == null) {
                throw new ServiceException(400, "plannedDate required (index=" + i + ")");
            }
            PaymentPlan p = new PaymentPlan();
            p.setContractId(contractId);
            p.setPlanNo(dto.getPlanNo() == null ? "P-" + (i + 1) : dto.getPlanNo());
            p.setPlannedAmount(dto.getPlannedAmount());
            p.setPlannedDate(dto.getPlannedDate());
            p.setStatus(STATUS_PENDING);
            p.setTenantId(ctx.getTenantId());
            plans.add(p);
            i++;
        }
        for (PaymentPlan p : plans) {
            paymentPlanMapper.insert(p);
        }
        log.info("Generated {} payment plans for contract={}", plans.size(), contractId);
        return plans.size();
    }

    public PaymentPlan get(Long id) {
        PaymentPlan p = paymentPlanMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "PaymentPlan not found: " + id);
        return p;
    }

    public List<PaymentPlan> findByContract(Long contractId) {
        requireTenant();
        // Simple select — TenantLineInnerInterceptor handles tenant_id automatically.
        return paymentPlanMapper.selectById(contractId) == null ? List.of()
            : paymentPlanMapper.findOverdue(LocalDate.now(), UserContextHolder.getTenantId())
                .stream().filter(p -> contractId.equals(p.getContractId())).toList();
    }

    /**
     * Mark a payment plan as completed (or partially completed).
     * Validation chain:
     *   1) actualAmount must be > 0
     *   2) actualAmount must be <= plannedAmount (security: never over-collect)
     *   3) if actualAmount == plannedAmount → status=completed; otherwise status=partial
     *
     * <p>Throws ServiceException with code 400 on validation failure.</p>
     */
    @Transactional
    public PaymentPlan markCompleted(Long planId, BigDecimal actualAmount, LocalDate actualDate) {
        if (actualAmount == null || actualAmount.signum() <= 0) {
            throw new ServiceException(400, "actualAmount must be positive");
        }
        PaymentPlan plan = get(planId);
        // planned_amount is immutable — we never update it. Use a local copy to enforce.
        BigDecimal planned = plan.getPlannedAmount() == null ? BigDecimal.ZERO : plan.getPlannedAmount();
        if (actualAmount.compareTo(planned) > 0) {
            throw new ServiceException(400,
                "actualAmount (" + actualAmount + ") cannot exceed plannedAmount (" + planned + ")");
        }
        Map<String, Object> before = new HashMap<>();
        before.put("status", plan.getStatus());
        before.put("actualAmount", plan.getActualAmount());
        plan.setActualAmount(actualAmount);
        plan.setActualDate(actualDate == null ? LocalDate.now() : actualDate);
        plan.setStatus(actualAmount.compareTo(planned) == 0 ? STATUS_COMPLETED : STATUS_PARTIAL);
        paymentPlanMapper.updateById(plan);
        log.info("PaymentPlan {} marked status={} actual={}/{}",
            planId, plan.getStatus(), actualAmount, planned);
        return plan;
    }

    /**
     * Quartz entry point — flips pending/partial past their planned_date to overdue.
     * Returns the affected list so callers (or controllers) can inspect what changed.
     */
    public List<PaymentPlan> findOverdue() {
        // TODO Quartz trigger — left as a manual call for now.
        List<PaymentPlan> overdue = paymentPlanMapper.findOverdue(
            LocalDate.now(), UserContextHolder.getTenantId());
        for (PaymentPlan p : overdue) {
            p.setStatus(STATUS_OVERDUE);
            paymentPlanMapper.updateById(p);
        }
        return overdue;
    }

    private UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    private void requireTenant() {
        if (UserContextHolder.get() == null || UserContextHolder.get().getTenantId() == null) {
            throw new ServiceException(401, "No tenant context");
        }
    }
}
