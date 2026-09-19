package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinBudget;
import com.lumen.finance.entity.FinBudgetItem;
import com.lumen.finance.mapper.FinBudgetItemMapper;
import com.lumen.finance.mapper.FinBudgetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算 service。check/consume 是核心: consume 超支 → 409。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_CLOSED = "closed";

    private final FinBudgetMapper budgetMapper;
    private final FinBudgetItemMapper itemMapper;

    public FinBudget get(Long id) {
        UserContext ctx = requireUserContext();
        FinBudget b = budgetMapper.selectById(id);
        if (b == null) throw new ServiceException(404, "Budget not found: " + id);
        assertTenant(b, ctx);
        return b;
    }

    public List<FinBudget> findByPeriodAndDept(String period, Long deptId) {
        requireTenant();
        return budgetMapper.findByPeriodAndDept(period, deptId);
    }

    @Transactional
    public FinBudget save(FinBudget req) {
        UserContext ctx = requireUserContext();
        if (req.getPeriod() == null || req.getPeriod().isBlank()) {
            throw new ServiceException(400, "period is required");
        }
        if (req.getUsedAmount() == null) req.setUsedAmount(BigDecimal.ZERO);
        if (req.getStatus() == null) req.setStatus(STATUS_OPEN);
        req.setTenantId(ctx.getTenantId());
        budgetMapper.insert(req);
        return req;
    }

    /**
     * 检查是否超支。true = OK / false = 超支; 调用方按需决定如何处理。
     */
    public boolean check(String period, Long deptId, Long subjectId, BigDecimal amount) {
        requireTenant();
        FinBudget b = budgetMapper.findOne(period, deptId, subjectId);
        if (b == null) {
            // No budget defined for (period, dept, subject) — conservatively allow but log.
            // Production systems may want to reject; for B1 we permit and let operators
            // configure budgets first.
            log.debug("No budget for period={} deptId={} subjectId={} — allowing", period, deptId, subjectId);
            return true;
        }
        BigDecimal remaining = b.getPlannedAmount().subtract(b.getUsedAmount());
        return amount == null || amount.compareTo(remaining) <= 0;
    }

    /**
     * 占用预算。超 planned_amount 抛 409。
     */
    @Transactional
    public FinBudget consume(String period, Long deptId, Long subjectId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(400, "amount must be positive");
        }
        requireTenant();
        FinBudget b = budgetMapper.findOne(period, deptId, subjectId);
        if (b == null) {
            throw new ServiceException(404,
                "Budget not defined for period=" + period + " deptId=" + deptId
                    + " subjectId=" + subjectId);
        }
        BigDecimal newUsed = b.getUsedAmount().add(amount);
        if (newUsed.compareTo(b.getPlannedAmount()) > 0) {
            // 安全要求 #8: 超支拦截
            throw new ServiceException(409,
                "Budget exceeded: planned=" + b.getPlannedAmount()
                    + " used=" + b.getUsedAmount() + " +amount=" + amount);
        }
        b.setUsedAmount(newUsed);
        budgetMapper.updateById(b);

        // Mirror to fin_budget_item if a matching item exists.
        FinBudgetItem item = itemMapper.findOne(b.getId(), subjectId);
        if (item != null) {
            item.setUsedAmount(item.getUsedAmount() == null ? BigDecimal.ZERO : item.getUsedAmount());
            BigDecimal newItemUsed = item.getUsedAmount().add(amount);
            item.setUsedAmount(newItemUsed);
            itemMapper.updateById(item);
        }
        log.info("Budget consumed budgetId={} +amount={} used={}/{}",
            b.getId(), amount, newUsed, b.getPlannedAmount());
        return b;
    }

    // ---------- helpers ----------
    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private UserContext requireTenant() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }

    private void assertTenant(FinBudget b, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(b.getTenantId())) {
            throw new ServiceException(404, "Budget not found: " + b.getId());
        }
    }
}
