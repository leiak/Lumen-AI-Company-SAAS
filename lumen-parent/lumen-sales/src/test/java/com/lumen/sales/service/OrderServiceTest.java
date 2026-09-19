package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import com.lumen.sales.mapper.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Order 全生命周期: draft → confirmed → shipping → shipped → completed。
 * cancel 校验状态机;complete 必须 shipment delivered 后才创建应收。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private ReceivableMapper receivableMapper;
    @Mock private CustomerService customerService;

    @InjectMocks private OrderService orderService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long ORDER_ID = 99L;
    private static final long CUSTOMER_ID = 88L;
    private static final long SHIP_ID = 77L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("sales")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Order stubOrder(String status) {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setCode("O-001");
        o.setCustomerId(CUSTOMER_ID);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("1000"));
        o.setOrderDate(LocalDate.now());
        o.setTenantId(TENANT);
        return o;
    }

    private Shipment stubShipment(String status) {
        Shipment s = new Shipment();
        s.setId(SHIP_ID);
        s.setOrderId(ORDER_ID);
        s.setStatus(status);
        s.setTenantId(TENANT);
        return s;
    }

    @Test
    void confirm_draft_flipsToConfirmed() {
        Order o = stubOrder(OrderService.STATUS_DRAFT);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        Order out = orderService.confirm(ORDER_ID);
        assertEquals(OrderService.STATUS_CONFIRMED, out.getStatus());
    }

    @Test
    void confirm_alreadyConfirmed_throws409() {
        Order o = stubOrder(OrderService.STATUS_CONFIRMED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.confirm(ORDER_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void markShipping_confirmedOrderCreatesShipment() {
        Order o = stubOrder(OrderService.STATUS_CONFIRMED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(shipmentMapper.insert(any(Shipment.class))).thenAnswer(inv -> {
            Shipment arg = inv.getArgument(0);
            arg.setId(SHIP_ID);
            return 1;
        });
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        Shipment s = orderService.markShipping(ORDER_ID, "顺丰", "SF123");
        assertEquals(OrderService.STATUS_SHIPPING, o.getStatus());
        assertNotNull(s);
        assertEquals(ORDER_ID, s.getOrderId());
        assertEquals(ShipmentService.STATUS_PENDING, s.getStatus());
    }

    @Test
    void markShipped_shippingOrderFlipsToShipped() {
        Order o = stubOrder(OrderService.STATUS_SHIPPING);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(shipmentMapper.findByOrder(ORDER_ID, TENANT))
            .thenReturn(List.of(stubShipment(ShipmentService.STATUS_PENDING)));
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        when(shipmentMapper.updateById(any(Shipment.class))).thenAnswer(inv -> 1);
        Order out = orderService.markShipped(ORDER_ID);
        assertEquals(OrderService.STATUS_SHIPPED, out.getStatus());
    }

    @Test
    void complete_shippedWithDeliveredShipmentCreatesReceivable() {
        Order o = stubOrder(OrderService.STATUS_SHIPPED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(shipmentMapper.findByOrder(ORDER_ID, TENANT))
            .thenReturn(List.of(stubShipment(ShipmentService.STATUS_DELIVERED)));
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        when(receivableMapper.insert(any(Receivable.class))).thenAnswer(inv -> {
            Receivable arg = inv.getArgument(0);
            arg.setId(500L);
            return 1;
        });

        Receivable r = orderService.complete(ORDER_ID);
        assertEquals(ReceivableService.STATUS_PENDING, r.getStatus());
        assertEquals(o.getTotalAmount(), r.getAmount());
        assertEquals(ORDER_ID, r.getOrderId());
        assertEquals(CUSTOMER_ID, r.getCustomerId());
        assertEquals(OrderService.STATUS_COMPLETED, o.getStatus());
    }

    @Test
    void complete_shipmentNotDelivered_throws409() {
        Order o = stubOrder(OrderService.STATUS_SHIPPED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(shipmentMapper.findByOrder(ORDER_ID, TENANT))
            .thenReturn(List.of(stubShipment(ShipmentService.STATUS_IN_TRANSIT)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.complete(ORDER_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void complete_alreadyCompleted_throws409() {
        Order o = stubOrder(OrderService.STATUS_COMPLETED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.complete(ORDER_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void cancel_draftOrder_legal() {
        Order o = stubOrder(OrderService.STATUS_DRAFT);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        Order out = orderService.cancel(ORDER_ID, "客户取消");
        assertEquals(OrderService.STATUS_CANCELLED, out.getStatus());
    }

    @Test
    void cancel_shippingOrder_legal() {
        Order o = stubOrder(OrderService.STATUS_SHIPPING);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> 1);
        Order out = orderService.cancel(ORDER_ID, "shipping issue");
        assertEquals(OrderService.STATUS_CANCELLED, out.getStatus());
    }

    @Test
    void cancel_completedOrder_throws409() {
        Order o = stubOrder(OrderService.STATUS_COMPLETED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.cancel(ORDER_ID, null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void cancellableStatuses_containsThreeStates() {
        assertEquals(3, orderService.cancellableStatuses().size());
        assertTrue(orderService.cancellableStatuses().contains(OrderService.STATUS_DRAFT));
        assertTrue(orderService.cancellableStatuses().contains(OrderService.STATUS_CONFIRMED));
        assertTrue(orderService.cancellableStatuses().contains(OrderService.STATUS_SHIPPING));
    }
}