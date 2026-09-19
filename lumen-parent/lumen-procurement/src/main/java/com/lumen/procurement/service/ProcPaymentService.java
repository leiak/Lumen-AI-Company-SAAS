package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.ApplyPaymentRequest;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcPayment;
import com.lumen.procurement.entity.ProcReceipt;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcPaymentMapper;
import com.lumen.procurement.mapper.ProcReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 付款单主流程。
 *
 * <p>状态机: pending → approved → paid / rejected。</p>
 * <p>安全要点:</p>
 * <ul>
 *   <li>apply: 仅订单 approved 状态可发起付款; receipt source 需 confirmed 状态。</li>
 *   <li>approve: 仅 pending 可批 → approved。</li>
 *   <li>markPaid: 仅 approved 可付 → paid, 联动把订单状态置 fulfilled (若订单已全部收货)。</li>
 *   <li>payableId: 跨服务引用 fin_payable.id, P4 阶段不校验 (TODO P5)。</li>
 *   <li>paymentNo UNIQUE。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcPaymentService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_REJECTED = "rejected";

    public static final String SOURCE_ORDER = "order";
    public static final String SOURCE_RECEIPT = "receipt";

    private final ProcPaymentMapper paymentMapper;
    private final ProcOrderMapper orderMapper;
    private final ProcReceiptMapper receiptMapper;
    private final ProcOrderService orderService;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcPayment> page(int pageNum, int pageSize, String status) {
        requireCtx();
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProcPayment>()
            .orderByDesc(ProcPayment::getId);
        if (status != null && !status.isBlank()) w.eq(ProcPayment::getStatus, status);
        return paymentMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcPayment getById(Long id) {
        UserContext ctx = requireCtx();
        ProcPayment p = paymentMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "Payment not found: " + id);
        if (!p.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Payment not found: " + id);
        }
        return p;
    }

    /**
     * 申请付款: pending。amount 必须 > 0, paymentNo UNIQUE。
     * sourceType=order: 校验订单 approved 状态。
     * sourceType=receipt: 校验收货 confirmed 状态。
     */
    @Transactional
    public ProcPayment apply(ApplyPaymentRequest req) {
        UserContext ctx = requireCtx();
        if (req.getPaymentNo() == null || req.getPaymentNo().isBlank()) {
            throw new ServiceException(400, "paymentNo is required");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new ServiceException(400, "amount must be positive");
        }
        if (paymentMapper.findByPaymentNo(ctx.getTenantId(), req.getPaymentNo()) != null) {
            throw new ServiceException(409, "paymentNo already exists: " + req.getPaymentNo());
        }
        // source 校验
        if (SOURCE_ORDER.equals(req.getSourceType())) {
            ProcOrder o = orderMapper.selectById(req.getSourceId());
            if (o == null || !o.getTenantId().equals(ctx.getTenantId())) {
                throw new ServiceException(404, "Source order not found");
            }
            if (!ProcOrderService.STATUS_APPROVED.equals(o.getStatus())
                && !ProcOrderService.STATUS_FULFILLED.equals(o.getStatus())) {
                throw new ServiceException(409, "Source order not in approved/fulfilled (current="
                    + o.getStatus() + ")");
            }
        } else if (SOURCE_RECEIPT.equals(req.getSourceType())) {
            ProcReceipt r = receiptMapper.selectById(req.getSourceId());
            if (r == null || !r.getTenantId().equals(ctx.getTenantId())) {
                throw new ServiceException(404, "Source receipt not found");
            }
            if (!ProcReceiptService.STATUS_CONFIRMED.equals(r.getStatus())) {
                throw new ServiceException(409, "Source receipt not confirmed (current=" + r.getStatus() + ")");
            }
        } else {
            throw new ServiceException(400, "sourceType must be order/receipt");
        }
        ProcPayment p = new ProcPayment();
        p.setTenantId(ctx.getTenantId());
        p.setPaymentNo(req.getPaymentNo());
        p.setSourceType(req.getSourceType());
        p.setSourceId(req.getSourceId());
        p.setPayableId(req.getPayableId());  // 跨服务引用, P4 不校验
        p.setAmount(req.getAmount());
        p.setPaymentMethod(req.getPaymentMethod());
        p.setStatus(STATUS_PENDING);
        p.setRequesterId(ctx.getUserId());
        try {
            paymentMapper.insert(p);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "paymentNo conflict", ex);
        }
        log.info("Payment applied id={} no={} amount={}", p.getId(), p.getPaymentNo(), req.getAmount());
        return p;
    }

    /**
     * 审批通过: pending → approved。
     */
    @Transactional
    public ProcPayment approve(Long id) {
        UserContext ctx = requireCtx();
        ProcPayment p = getById(id);
        if (!STATUS_PENDING.equals(p.getStatus())) {
            throw new ServiceException(409, "Only pending payment can be approved (current=" + p.getStatus() + ")");
        }
        p.setStatus(STATUS_APPROVED);
        p.setApproverId(ctx.getUserId());
        paymentMapper.updateById(p);
        log.info("Payment approved id={} approver={}", id, p.getApproverId());
        return p;
    }

    /**
     * 审批驳回: pending → rejected。
     */
    @Transactional
    public ProcPayment reject(Long id, String reason) {
        ProcPayment p = getById(id);
        if (!STATUS_PENDING.equals(p.getStatus())) {
            throw new ServiceException(409, "Only pending payment can be rejected (current=" + p.getStatus() + ")");
        }
        p.setStatus(STATUS_REJECTED);
        paymentMapper.updateById(p);
        log.info("Payment rejected id={} reason={}", id, reason);
        return p;
    }

    /**
     * 标记已付款: approved → paid, 设置 paid_at。
     * 联动: 若 source 是 order 且 order 已全部收货, 把 order 标记 fulfilled
     * (避免在 markFulfilled 中手动调用 — 这是合理的 final 闭环)。
     */
    @Transactional
    public ProcPayment markPaid(Long id) {
        ProcPayment p = getById(id);
        if (!STATUS_APPROVED.equals(p.getStatus())) {
            throw new ServiceException(409, "Only approved payment can be marked paid (current=" + p.getStatus() + ")");
        }
        p.setStatus(STATUS_PAID);
        p.setPaidAt(LocalDateTime.now());
        paymentMapper.updateById(p);
        // 联动: order source 若已完成全部收货 → markFulfilled
        if (SOURCE_ORDER.equals(p.getSourceType())) {
            try {
                ProcOrder o = orderMapper.selectById(p.getSourceId());
                if (o != null && ProcOrderService.STATUS_APPROVED.equals(o.getStatus())) {
                    orderService.markFulfilled(o.getId());
                }
            } catch (ServiceException ex) {
                // 若未完全收货 (收到非 409), 不阻断付款; 仅日志
                log.warn("Order not fulfilled yet: paymentId={} reason={}", id, ex.getMessage());
            }
        }
        log.info("Payment paid id={}", id);
        return p;
    }

    public List<ProcPayment> findBySource(String sourceType, Long sourceId) {
        UserContext ctx = requireCtx();
        return paymentMapper.findBySource(ctx.getTenantId(), sourceType, sourceId);
    }

    public List<ProcPayment> findByStatus(String status) {
        UserContext ctx = requireCtx();
        return paymentMapper.findByStatus(ctx.getTenantId(), status);
    }
}