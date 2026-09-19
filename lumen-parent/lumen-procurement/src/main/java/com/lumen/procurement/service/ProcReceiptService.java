package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.ConfirmReceiptRequest;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcOrderItem;
import com.lumen.procurement.entity.ProcReceipt;
import com.lumen.procurement.mapper.ProcOrderItemMapper;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 收货单主流程。
 *
 * <p>状态机: pending → confirmed / discrepancy (差异)。</p>
 * <p>安全要点:</p>
 * <ul>
 *   <li>create: 自动从 order_item 生成初始 receipt (pending); code 唯一。</li>
 *   <li>confirm: 累计 order_item.received_quantity; 若任一明细数量不匹配 → status=discrepancy。</li>
 *   <li>received_quantity 不能超过 quantity (校验)。</li>
 *   <li>差异时 TODO 发消息通知 (留 P5)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcReceiptService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_DISCREPANCY = "discrepancy";

    private final ProcReceiptMapper receiptMapper;
    private final ProcOrderMapper orderMapper;
    private final ProcOrderItemMapper orderItemMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcReceipt> page(int pageNum, int pageSize, Long orderId, String status) {
        requireCtx();
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProcReceipt>()
            .orderByDesc(ProcReceipt::getId);
        if (orderId != null) w.eq(ProcReceipt::getOrderId, orderId);
        if (status != null && !status.isBlank()) w.eq(ProcReceipt::getStatus, status);
        return receiptMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcReceipt getById(Long id) {
        UserContext ctx = requireCtx();
        ProcReceipt r = receiptMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Receipt not found: " + id);
        if (!r.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Receipt not found: " + id);
        }
        return r;
    }

    /**
     * 为指定采购单创建初始收货单 (pending)。
     * 若 code 已存在 → 409。
     */
    @Transactional
    public ProcReceipt create(Long orderId, String code, LocalDate receiptDate) {
        UserContext ctx = requireCtx();
        ProcOrder o = orderMapper.selectById(orderId);
        if (o == null || !o.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Order not found");
        }
        if (code == null || code.isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (receiptMapper.findByCode(ctx.getTenantId(), code) != null) {
            throw new ServiceException(409, "Receipt code already exists: " + code);
        }
        ProcReceipt r = new ProcReceipt();
        r.setTenantId(ctx.getTenantId());
        r.setCode(code);
        r.setOrderId(orderId);
        r.setReceiptDate(receiptDate == null ? LocalDate.now() : receiptDate);
        r.setStatus(STATUS_PENDING);
        try {
            receiptMapper.insert(r);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Receipt code conflict", ex);
        }
        log.info("Receipt created id={} code={} order={}", r.getId(), r.getCode(), orderId);
        return r;
    }

    /**
     * 确认收货: actualQuantities: itemId -> actualQty。
     * - 累计 order_item.received_quantity (多次收货可叠加)。
     * - 若任一明细 received < ordered → discrepancy。
     * - 若全部 received >= ordered → confirmed。
     * - 若任一 actualQty > ordered → 409 (不允许超收)。
     */
    @Transactional
    public ProcReceipt confirm(Long id, ConfirmReceiptRequest req) {
        UserContext ctx = requireCtx();
        ProcReceipt r = getById(id);
        if (!STATUS_PENDING.equals(r.getStatus())) {
            throw new ServiceException(409, "Only pending receipt can be confirmed (current=" + r.getStatus() + ")");
        }
        if (req.getActualQuantities() == null || req.getActualQuantities().isEmpty()) {
            throw new ServiceException(400, "actualQuantities must not be empty");
        }
        List<ProcOrderItem> items = orderItemMapper.findByOrder(ctx.getTenantId(), r.getOrderId());
        Map<Long, Long> actuals = req.getActualQuantities();
        boolean hasDiscrepancy = false;
        List<String> discrepancies = new ArrayList<>();
        for (ProcOrderItem it : items) {
            Long actual = actuals.get(it.getId());
            if (actual == null) continue;  // 此条未录入视为未到货
            if (actual < 0) {
                throw new ServiceException(400, "actualQty must be >= 0 for item " + it.getId());
            }
            int ord = it.getQuantity() == null ? 0 : it.getQuantity();
            if (actual > ord) {
                throw new ServiceException(409, "Item " + it.getId()
                    + " actualQty " + actual + " exceeds ordered " + ord);
            }
            int prev = it.getReceivedQuantity() == null ? 0 : it.getReceivedQuantity();
            int newRec = prev + actual.intValue();
            if (newRec > ord) {
                throw new ServiceException(409, "Item " + it.getId()
                    + " cumulative received " + newRec + " exceeds ordered " + ord);
            }
            it.setReceivedQuantity(newRec);
            orderItemMapper.updateById(it);
            if (newRec < ord) {
                hasDiscrepancy = true;
                discrepancies.add("item=" + it.getId() + " " + newRec + "/" + ord);
            }
        }
        r.setInspectorId(req.getInspectorId() == null ? ctx.getUserId() : req.getInspectorId());
        if (hasDiscrepancy) {
            r.setStatus(STATUS_DISCREPANCY);
            log.warn("Receipt discrepancy id={} order={} details={}", id, r.getOrderId(), discrepancies);
            // TODO P5: 发消息通知 procurement_admin + 采购员
        } else {
            r.setStatus(STATUS_CONFIRMED);
        }
        receiptMapper.updateById(r);
        return r;
    }

    /**
     * Pending 收货单 (dashboard)。
     */
    public List<ProcReceipt> findPending() {
        UserContext ctx = requireCtx();
        return receiptMapper.findPending(ctx.getTenantId());
    }
}