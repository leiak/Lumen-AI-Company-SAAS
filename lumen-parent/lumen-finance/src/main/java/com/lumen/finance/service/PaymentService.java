package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinExpenseReport;
import com.lumen.finance.entity.FinPayable;
import com.lumen.finance.entity.FinPayment;
import com.lumen.finance.mapper.FinExpenseReportMapper;
import com.lumen.finance.mapper.FinPayableMapper;
import com.lumen.finance.mapper.FinPaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 付款 service。按 sourceType 路由联动更新 payable / expense 状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String SOURCE_PAYABLE = "payable";
    public static final String SOURCE_EXPENSE = "expense";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";

    private final FinPaymentMapper paymentMapper;
    private final FinPayableMapper payableMapper;
    private final FinExpenseReportMapper expenseMapper;
    private final PayableService payableService;
    private final ExpenseReportService expenseService;

    public FinPayment get(Long id) {
        UserContext ctx = requireUserContext();
        FinPayment p = paymentMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "Payment not found: " + id);
        assertTenant(p, ctx);
        return p;
    }

    public List<FinPayment> findBySource(String sourceType, Long sourceId) {
        requireTenant();
        return paymentMapper.findBySource(sourceType, sourceId);
    }

    /**
     * 创建付款单并按 sourceType 联动:
     * - payable: 累加 payable.paidAmount, 全部付清则 status=paid
     * - expense: 立即把 expense.status 置为 paid
     */
    @Transactional
    public FinPayment create(String sourceType, Long sourceId, BigDecimal amount,
                              String payee, String paymentMethod) {
        UserContext ctx = requireUserContext();
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(400, "amount must be positive");
        }
        if (sourceType == null) throw new ServiceException(400, "sourceType is required");
        if (sourceId == null) throw new ServiceException(400, "sourceId is required");

        // Source existence / tenant check (use existing services for consistency)
        if (SOURCE_PAYABLE.equals(sourceType)) {
            // cross-tenant → 404 via get()
            FinPayable p = payableService.get(sourceId);
            BigDecimal remaining = p.getAmount().subtract(p.getPaidAmount());
            if (amount.compareTo(remaining) > 0) {
                throw new ServiceException(400,
                    "Payment amount exceeds remaining payable: " + amount + " > " + remaining);
            }
            payableService.pay(sourceId, amount);
        } else if (SOURCE_EXPENSE.equals(sourceType)) {
            FinExpenseReport er = expenseService.get(sourceId);
            if (!ExpenseReportService.STATUS_APPROVED.equals(er.getStatus())) {
                throw new ServiceException(409,
                    "Only approved expense reports can be paid; status=" + er.getStatus());
            }
            if (amount.compareTo(er.getTotalAmount()) > 0) {
                throw new ServiceException(400,
                    "Payment amount exceeds expense total: " + amount + " > " + er.getTotalAmount());
            }
            expenseService.markPaid(sourceId);
        } else {
            throw new ServiceException(400,
                "sourceType must be one of [payable, expense], got: " + sourceType);
        }

        FinPayment p = new FinPayment();
        p.setTenantId(ctx.getTenantId());
        p.setPaymentNo(generatePaymentNo());
        p.setSourceType(sourceType);
        p.setSourceId(sourceId);
        p.setAmount(amount);
        p.setPayee(payee);
        p.setPaymentMethod(paymentMethod);
        p.setPaidAt(LocalDateTime.now());
        p.setStatus(STATUS_DONE);
        paymentMapper.insert(p);
        log.info("Payment created id={} no={} sourceType={} sourceId={} amount={}",
            p.getId(), p.getPaymentNo(), sourceType, sourceId, amount);
        return p;
    }

    private String generatePaymentNo() {
        return "P-" + LocalDateTime.now().toString().replace(":", "").replace("-", "").replace(".", "")
            + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    private void assertTenant(FinPayment p, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(p.getTenantId())) {
            throw new ServiceException(404, "Payment not found: " + p.getId());
        }
    }
}
