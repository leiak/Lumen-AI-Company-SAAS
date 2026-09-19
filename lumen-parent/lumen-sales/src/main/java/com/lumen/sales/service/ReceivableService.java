package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.PaymentRecord;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.mapper.PaymentRecordMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 应收账款服务。
 * <p>
 * recordPayment:创建 payment_record,累加 receivable.collected_amount,自动转 status。
 * pending → partial (部分收款) → collected (收齐)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_COLLECTED = "collected";
    public static final String STATUS_OVERDUE = "overdue";

    private final ReceivableMapper receivableMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final CustomerService customerService;

    public IPage<Receivable> page(int pageNum, int pageSize, String status, Long customerId) {
        var w = new LambdaQueryWrapper<Receivable>().orderByDesc(Receivable::getId);
        if (status != null && !status.isBlank()) w.eq(Receivable::getStatus, status);
        if (customerId != null) w.eq(Receivable::getCustomerId, customerId);
        return receivableMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Receivable get(Long id) {
        Receivable r = receivableMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Receivable not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(r.getTenantId())) {
                throw new ServiceException(404, "Receivable not found: " + id);
            }
        }
        return r;
    }

    public List<PaymentRecord> findPayments(Long receivableId) {
        UserContext ctx = requireContext();
        get(receivableId); // tenant guard
        return paymentRecordMapper.findByReceivable(receivableId, ctx.getTenantId());
    }

    public List<Receivable> findOverdue() {
        UserContext ctx = requireContext();
        return receivableMapper.findOverdue(LocalDate.now(), ctx.getTenantId());
    }

    @Transactional
    public Receivable save(Receivable req) {
        UserContext ctx = requireContext();
        Receivable r = new Receivable();
        r.setCode("R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setOrderId(req.getOrderId());
        r.setCustomerId(req.getCustomerId());
        r.setAmount(req.getAmount() == null ? BigDecimal.ZERO : req.getAmount());
        r.setDueDate(req.getDueDate());
        r.setStatus(STATUS_PENDING);
        r.setCollectedAmount(BigDecimal.ZERO);
        r.setTenantId(ctx.getTenantId());
        try {
            receivableMapper.insert(r);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Receivable code conflict", ex);
        }
        return r;
    }

    /**
     * 登记一笔回款。
     * <p>
     * 校验 amount ≤ receivable.amount - receivable.collected_amount。
     * 累加 collected_amount;若 == amount → status=collected;若 < amount → partial;否则 pending。
     * 同时如果 due_date 已过,标记 overdue。
     */
    @Transactional
    public PaymentRecord recordPayment(Long receivableId, BigDecimal amount, String method, String remark) {
        UserContext ctx = requireContext();
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(400, "amount must be > 0");
        }
        Receivable r = get(receivableId);
        BigDecimal remain = r.getAmount() == null ? BigDecimal.ZERO
            : r.getAmount().subtract(
                r.getCollectedAmount() == null ? BigDecimal.ZERO : r.getCollectedAmount());
        if (amount.compareTo(remain) > 0) {
            throw new ServiceException(409,
                "Payment amount exceeds outstanding: amount=" + amount + " remain=" + remain);
        }

        PaymentRecord pr = new PaymentRecord();
        pr.setReceivableId(receivableId);
        pr.setAmount(amount);
        pr.setPaymentMethod(method);
        pr.setPaidAt(LocalDateTime.now());
        pr.setOperatorId(ctx.getUserId());
        pr.setRemark(remark);
        pr.setTenantId(ctx.getTenantId());
        paymentRecordMapper.insert(pr);

        BigDecimal newCollected = (r.getCollectedAmount() == null ? BigDecimal.ZERO : r.getCollectedAmount())
            .add(amount);
        r.setCollectedAmount(newCollected);
        if (newCollected.compareTo(r.getAmount()) >= 0) {
            r.setStatus(STATUS_COLLECTED);
        } else {
            r.setStatus(STATUS_PARTIAL);
        }
        if (r.getDueDate() != null && r.getDueDate().isBefore(LocalDate.now())
            && !STATUS_COLLECTED.equals(r.getStatus())) {
            r.setStatus(STATUS_OVERDUE);
        }
        receivableMapper.updateById(r);
        return pr;
    }

    /** Find receivables that are overdue (due_date < today AND not collected). */
    public List<Receivable> findOverdueNow() {
        return findOverdue();
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}