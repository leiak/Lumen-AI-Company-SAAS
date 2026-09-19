package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.CreateOrderRequest;
import com.lumen.procurement.dto.OrderItemDto;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcOrderItem;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcOrderItemMapper;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购单主流程。
 *
 * <p>状态机: draft → submitted → approved/rejected → fulfilled/cancelled。</p>
 * <p>安全要点:</p>
 * <ul>
 *   <li>submit: draft → submitted (触发 workflow TODO)。</li>
 *   <li>approve: 仅 submitted 可批准 → approved。</li>
 *   <li>reject: 仅 submitted 可驳回 → rejected。</li>
 *   <li>cancel: 仅 draft / submitted 可取消 → cancelled。</li>
 *   <li>markFulfilled: 所有 order_item.received_quantity >= quantity 才可标完成。</li>
 *   <li>黑名单供应商不能被下单。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcOrderService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_FULFILLED = "fulfilled";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String SOURCE_QUOTATION = "quotation";
    public static final String SOURCE_BIDDING = "bidding";
    public static final String SOURCE_DIRECT = "direct";

    private final ProcOrderMapper orderMapper;
    private final ProcOrderItemMapper orderItemMapper;
    private final ProcSupplierMapper supplierMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcOrder> page(int pageNum, int pageSize, Long supplierId, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<ProcOrder>().orderByDesc(ProcOrder::getId);
        if (supplierId != null) w.eq(ProcOrder::getSupplierId, supplierId);
        if (status != null && !status.isBlank()) w.eq(ProcOrder::getStatus, status);
        return orderMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcOrder getById(Long id) {
        UserContext ctx = requireCtx();
        ProcOrder o = orderMapper.selectById(id);
        if (o == null) throw new ServiceException(404, "Order not found: " + id);
        if (!o.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Order not found: " + id);
        }
        return o;
    }

    public List<ProcOrderItem> listItems(Long orderId) {
        UserContext ctx = requireCtx();
        getById(orderId);
        return orderItemMapper.findByOrder(ctx.getTenantId(), orderId);
    }

    /**
     * 直接创建采购单 (source_type=direct) 或从 quotation/bidding 调用
     * {@link ProcInquiryService#createOrderFromQuotation}。
     */
    @Transactional
    public ProcOrder create(CreateOrderRequest req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getSupplierId() == null) {
            throw new ServiceException(400, "supplierId is required");
        }
        if (req.getSourceType() == null) {
            throw new ServiceException(400, "sourceType is required");
        }
        // sourceType 枚举
        if (!SOURCE_QUOTATION.equals(req.getSourceType())
            && !SOURCE_BIDDING.equals(req.getSourceType())
            && !SOURCE_DIRECT.equals(req.getSourceType())) {
            throw new ServiceException(400, "sourceType must be quotation/bidding/direct");
        }
        // sourceId: quotation/bidding 必须非空
        if (!SOURCE_DIRECT.equals(req.getSourceType()) && req.getSourceId() == null) {
            throw new ServiceException(400, "sourceId is required when sourceType != direct");
        }
        ProcSupplier s = supplierMapper.selectById(req.getSupplierId());
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(400, "Invalid supplierId");
        }
        if (ProcSupplierService.STATUS_BLACKLIST.equals(s.getStatus())) {
            throw new ServiceException(409, "Supplier is blacklisted");
        }
        if (orderMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Order code already exists: " + req.getCode());
        }
        // 计算 totalAmount + 插入明细
        BigDecimal total = BigDecimal.ZERO;
        List<ProcOrderItem> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (OrderItemDto dto : req.getItems()) {
                ProcOrderItem it = new ProcOrderItem();
                it.setTenantId(ctx.getTenantId());
                it.setItemName(dto.getItemName());
                it.setSku(dto.getSku());
                it.setQuantity(dto.getQuantity());
                it.setUnitPrice(dto.getUnitPrice());
                BigDecimal sub = dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
                it.setSubtotal(sub);
                it.setReceivedQuantity(0);
                total = total.add(sub);
                items.add(it);
            }
        }
        ProcOrder o = new ProcOrder();
        o.setTenantId(ctx.getTenantId());
        o.setCode(req.getCode());
        o.setSupplierId(req.getSupplierId());
        o.setSourceType(req.getSourceType());
        o.setSourceId(req.getSourceId());
        o.setTotalAmount(total);
        o.setOrderDate(req.getOrderDate());
        o.setExpectedDeliveryAt(req.getExpectedDeliveryAt());
        o.setStatus(STATUS_DRAFT);
        try {
            orderMapper.insert(o);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Order code conflict", ex);
        }
        // 关联明细
        for (ProcOrderItem it : items) {
            it.setOrderId(o.getId());
            orderItemMapper.insert(it);
        }
        log.info("Order created id={} code={} items={}", o.getId(), o.getCode(), items.size());
        return o;
    }

    /**
     * 提交审批: draft → submitted。
     * TODO P5: 触发 workflow.startInstance
     */
    @Transactional
    public ProcOrder submit(Long id) {
        ProcOrder o = getById(id);
        if (!STATUS_DRAFT.equals(o.getStatus())) {
            throw new ServiceException(409, "Only draft order can be submitted (current=" + o.getStatus() + ")");
        }
        o.setStatus(STATUS_SUBMITTED);
        orderMapper.updateById(o);
        log.info("Order submitted id={}", id);
        return o;
    }

    /**
     * 审批通过: submitted → approved。
     * 由 workflow callback 触发。
     */
    @Transactional
    public ProcOrder approve(Long id, Long approverId) {
        ProcOrder o = getById(id);
        if (!STATUS_SUBMITTED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only submitted order can be approved (current=" + o.getStatus() + ")");
        }
        o.setStatus(STATUS_APPROVED);
        o.setApproverId(approverId);
        o.setApprovedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        log.info("Order approved id={} approver={}", id, approverId);
        return o;
    }

    /**
     * 审批驳回: submitted → rejected。
     */
    @Transactional
    public ProcOrder reject(Long id, String reason) {
        ProcOrder o = getById(id);
        if (!STATUS_SUBMITTED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only submitted order can be rejected (current=" + o.getStatus() + ")");
        }
        o.setStatus(STATUS_REJECTED);
        orderMapper.updateById(o);
        log.info("Order rejected id={} reason={}", id, reason);
        return o;
    }

    /**
     * 取消: draft / submitted → cancelled。
     */
    @Transactional
    public ProcOrder cancel(Long id, String reason) {
        ProcOrder o = getById(id);
        if (!STATUS_DRAFT.equals(o.getStatus()) && !STATUS_SUBMITTED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only draft/submitted order can be cancelled (current=" + o.getStatus() + ")");
        }
        o.setStatus(STATUS_CANCELLED);
        orderMapper.updateById(o);
        log.info("Order cancelled id={} reason={}", id, reason);
        return o;
    }

    /**
     * 标记完成: 所有 order_item.received_quantity >= quantity 才可标完成。
     */
    @Transactional
    public ProcOrder markFulfilled(Long id) {
        ProcOrder o = getById(id);
        if (!STATUS_APPROVED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only approved order can be fulfilled (current=" + o.getStatus() + ")");
        }
        List<ProcOrderItem> items = orderItemMapper.findByOrder(o.getTenantId(), id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Order has no items; cannot mark fulfilled");
        }
        for (ProcOrderItem it : items) {
            int rec = it.getReceivedQuantity() == null ? 0 : it.getReceivedQuantity();
            int ord = it.getQuantity() == null ? 0 : it.getQuantity();
            if (rec < ord) {
                throw new ServiceException(409, "Item " + it.getId()
                    + " not fully received: " + rec + "/" + ord);
            }
        }
        o.setStatus(STATUS_FULFILLED);
        orderMapper.updateById(o);
        log.info("Order fulfilled id={}", id);
        return o;
    }
}