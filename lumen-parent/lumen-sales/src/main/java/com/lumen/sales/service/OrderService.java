package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import com.lumen.sales.mapper.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 销售订单。状态机:
 * <pre>
 *   draft -> confirmed -> shipping -> shipped -> completed
 *               \-> cancelled
 * </pre>
 * 单向;cancel 只允许 draft/confirmed/shipping;completed 创建应收 (pending)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_SHIPPING = "shipping";
    public static final String STATUS_SHIPPED = "shipped";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final Set<String> CANCELLABLE = Set.of(
        STATUS_DRAFT, STATUS_CONFIRMED, STATUS_SHIPPING);

    private final OrderMapper orderMapper;
    private final ShipmentMapper shipmentMapper;
    private final ReceivableMapper receivableMapper;
    private final CustomerService customerService;

    public IPage<Order> page(int pageNum, int pageSize, String status, Long customerId) {
        var w = new LambdaQueryWrapper<Order>().orderByDesc(Order::getId);
        if (status != null && !status.isBlank()) w.eq(Order::getStatus, status);
        if (customerId != null) w.eq(Order::getCustomerId, customerId);
        return orderMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Order get(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new ServiceException(404, "Order not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(o.getTenantId())) {
                throw new ServiceException(404, "Order not found: " + id);
            }
        }
        return o;
    }

    @Transactional
    public Order save(Order req) {
        UserContext ctx = requireContext();
        if (req.getCustomerId() == null) {
            throw new ServiceException(400, "customerId is required");
        }
        customerService.get(req.getCustomerId());
        Order o = new Order();
        o.setCode("O-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        o.setCustomerId(req.getCustomerId());
        o.setContractId(req.getContractId());
        o.setSourceType(req.getSourceType() == null ? "direct" : req.getSourceType());
        o.setSourceId(req.getSourceId());
        o.setTotalAmount(req.getTotalAmount() == null ? BigDecimal.ZERO : req.getTotalAmount());
        o.setOrderDate(req.getOrderDate() == null ? LocalDate.now() : req.getOrderDate());
        o.setStatus(STATUS_DRAFT);
        o.setTenantId(ctx.getTenantId());
        try {
            orderMapper.insert(o);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Order code conflict", ex);
        }
        return o;
    }

    /** draft → confirmed。 */
    @Transactional
    public Order confirm(Long id) {
        Order o = get(id);
        if (!STATUS_DRAFT.equals(o.getStatus())) {
            throw new ServiceException(409, "Only draft order can be confirmed: " + o.getStatus());
        }
        o.setStatus(STATUS_CONFIRMED);
        orderMapper.updateById(o);
        return o;
    }

    /**
     * confirmed → shipping。同时创建一个 pending 的 shipment。
     */
    @Transactional
    public Shipment markShipping(Long id, String carrier, String trackingNo) {
        Order o = get(id);
        if (!STATUS_CONFIRMED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only confirmed order can be shipping: " + o.getStatus());
        }
        Shipment s = new Shipment();
        s.setCode("S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        s.setOrderId(o.getId());
        s.setShipmentDate(LocalDate.now());
        s.setCarrier(carrier);
        s.setTrackingNo(trackingNo);
        s.setStatus(ShipmentService.STATUS_PENDING);
        s.setTenantId(o.getTenantId());
        shipmentMapper.insert(s);

        o.setStatus(STATUS_SHIPPING);
        orderMapper.updateById(o);
        return s;
    }

    /** shipping → shipped — 标记 shipment.shipped。 */
    @Transactional
    public Order markShipped(Long id) {
        UserContext ctx = requireContext();
        Order o = get(id);
        if (!STATUS_SHIPPING.equals(o.getStatus())) {
            throw new ServiceException(409, "Only shipping order can be shipped: " + o.getStatus());
        }
        List<Shipment> ships = shipmentMapper.findByOrder(o.getId(), ctx.getTenantId());
        if (ships.isEmpty()) {
            throw new ServiceException(409, "No shipment for order " + id);
        }
        for (Shipment s : ships) {
            if (ShipmentService.STATUS_PENDING.equals(s.getStatus())
                || ShipmentService.STATUS_IN_TRANSIT.equals(s.getStatus())) {
                s.setStatus(ShipmentService.STATUS_IN_TRANSIT);
                shipmentMapper.updateById(s);
            }
        }
        o.setStatus(STATUS_SHIPPED);
        orderMapper.updateById(o);
        return o;
    }

    /**
     * shipped → completed。所有 shipment 必须 delivered,然后创建 receivable(pending)。
     */
    @Transactional
    public Receivable complete(Long id) {
        UserContext ctx = requireContext();
        Order o = get(id);
        if (!STATUS_SHIPPED.equals(o.getStatus())) {
            throw new ServiceException(409, "Only shipped order can be completed: " + o.getStatus());
        }
        List<Shipment> ships = shipmentMapper.findByOrder(o.getId(), ctx.getTenantId());
        if (ships.isEmpty()) {
            throw new ServiceException(409, "No shipment for order " + id);
        }
        for (Shipment s : ships) {
            if (!ShipmentService.STATUS_DELIVERED.equals(s.getStatus())) {
                throw new ServiceException(409,
                    "Order cannot complete: shipment " + s.getId() + " not delivered");
            }
        }
        o.setStatus(STATUS_COMPLETED);
        orderMapper.updateById(o);

        Receivable r = new Receivable();
        r.setCode("R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setOrderId(o.getId());
        r.setCustomerId(o.getCustomerId());
        r.setAmount(o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount());
        r.setDueDate(LocalDate.now().plusDays(30));
        r.setStatus(ReceivableService.STATUS_PENDING);
        r.setCollectedAmount(BigDecimal.ZERO);
        r.setTenantId(ctx.getTenantId());
        receivableMapper.insert(r);
        return r;
    }

    @Transactional
    public Order cancel(Long id, String reason) {
        Order o = get(id);
        if (!CANCELLABLE.contains(o.getStatus())) {
            throw new ServiceException(409,
                "Order cannot be cancelled at status=" + o.getStatus());
        }
        o.setStatus(STATUS_CANCELLED);
        orderMapper.updateById(o);
        log.info("Order {} cancelled: {}", id, reason);
        return o;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    public Set<String> cancellableStatuses() {
        return CANCELLABLE;
    }

    public Map<String, Set<String>> orderFlow() {
        return Map.of(
            STATUS_DRAFT, Set.of(STATUS_CONFIRMED, STATUS_CANCELLED),
            STATUS_CONFIRMED, Set.of(STATUS_SHIPPING, STATUS_CANCELLED),
            STATUS_SHIPPING, Set.of(STATUS_SHIPPED, STATUS_CANCELLED),
            STATUS_SHIPPED, Set.of(STATUS_COMPLETED),
            STATUS_COMPLETED, Set.of(),
            STATUS_CANCELLED, Set.of()
        );
    }
}