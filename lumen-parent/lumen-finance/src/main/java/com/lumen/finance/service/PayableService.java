package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinPayable;
import com.lumen.finance.mapper.FinPayableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 应付单 service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayableService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_PAID = "paid";

    private final FinPayableMapper payableMapper;

    public FinPayable get(Long id) {
        UserContext ctx = requireUserContext();
        FinPayable p = payableMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "Payable not found: " + id);
        assertTenant(p, ctx);
        return p;
    }

    @Transactional
    public FinPayable save(FinPayable req) {
        UserContext ctx = requireUserContext();
        if (req.getPaidAmount() == null) req.setPaidAmount(BigDecimal.ZERO);
        if (req.getStatus() == null) req.setStatus(STATUS_PENDING);
        req.setTenantId(ctx.getTenantId());
        payableMapper.insert(req);
        return req;
    }

    /**
     * 付款累积。amount > 0, 累加后 paidAmount 不能超过 amount。
     */
    @Transactional
    public FinPayable pay(Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(400, "amount must be positive");
        }
        FinPayable p = get(id);
        if (STATUS_PAID.equals(p.getStatus())) {
            throw new ServiceException(409, "Payable already fully paid");
        }
        BigDecimal newPaid = p.getPaidAmount().add(amount);
        if (newPaid.compareTo(p.getAmount()) > 0) {
            throw new ServiceException(400,
                "Paid amount would exceed payable amount: " + newPaid + " > " + p.getAmount());
        }
        p.setPaidAmount(newPaid);
        if (newPaid.compareTo(p.getAmount()) == 0) {
            p.setStatus(STATUS_PAID);
        } else {
            p.setStatus(STATUS_PARTIAL);
        }
        payableMapper.updateById(p);
        log.info("Payable paid id={} +amount={} status={}", p.getId(), amount, p.getStatus());
        return p;
    }

    // ---------- helpers ----------
    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private void assertTenant(FinPayable p, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(p.getTenantId())) {
            throw new ServiceException(404, "Payable not found: " + p.getId());
        }
    }
}
