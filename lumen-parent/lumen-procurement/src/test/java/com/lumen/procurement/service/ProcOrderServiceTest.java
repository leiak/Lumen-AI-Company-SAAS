package com.lumen.procurement.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcOrderServiceTest {

    @Mock private ProcOrderMapper orderMapper;
    @Mock private ProcOrderItemMapper orderItemMapper;
    @Mock private ProcSupplierMapper supplierMapper;
    @InjectMocks private ProcOrderService orderService;

    private static final long TENANT = 1L;
    private static final long ORDER_ID = 100L;
    private static final long SUPPLIER_ID = 5L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(10L).tenantId(TENANT).userName("alice")
            .roles(Set.of("procurement_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private ProcOrder stub(String status) {
        ProcOrder o = new ProcOrder();
        o.setId(ORDER_ID);
        o.setCode("PO-001");
        o.setSupplierId(SUPPLIER_ID);
        o.setTotalAmount(new BigDecimal("100"));
        o.setOrderDate(LocalDate.now());
        o.setStatus(status);
        o.setTenantId(TENANT);
        return o;
    }

    private ProcSupplier supplier(String status) {
        ProcSupplier s = new ProcSupplier();
        s.setId(SUPPLIER_ID);
        s.setCode("SUP");
        s.setName("Co");
        s.setStatus(status);
        s.setTenantId(TENANT);
        return s;
    }

    // 28. cross-tenant → 404
    @Test
    void getById_crossTenant_returns404() {
        ProcOrder o = stub("draft");
        o.setTenantId(99L);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.getById(ORDER_ID));
        assertEquals(404, ex.getCode());
    }

    // 29. cancel 只能 draft/submitted
    @Test
    void cancel_fromApproved_throws409() {
        ProcOrder o = stub("approved");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.cancel(ORDER_ID, "test"));
        assertEquals(409, ex.getCode());
    }

    // 30. approve 必须 submitted
    @Test
    void approve_fromDraft_throws409() {
        ProcOrder o = stub("draft");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.approve(ORDER_ID, 10L));
        assertEquals(409, ex.getCode());
    }

    // 31. submit draft → submitted
    @Test
    void submit_draft_setsSubmitted() {
        ProcOrder o = stub("draft");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ProcOrder result = orderService.submit(ORDER_ID);
        assertEquals("submitted", result.getStatus());
    }

    // 32. markFulfilled 校验所有 received >= ordered
    @Test
    void markFulfilled_partialReceived_throws409() {
        ProcOrder o = stub("approved");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ProcOrderItem it = new ProcOrderItem();
        it.setId(1L);
        it.setQuantity(10);
        it.setReceivedQuantity(5);
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(it)));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.markFulfilled(ORDER_ID));
        assertEquals(409, ex.getCode());
    }

    // 33. markFulfilled 全部收到 → fulfilled
    @Test
    void markFulfilled_allReceived_setsFulfilled() {
        ProcOrder o = stub("approved");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ProcOrderItem it = new ProcOrderItem();
        it.setId(1L);
        it.setQuantity(10);
        it.setReceivedQuantity(10);
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(it)));
        ProcOrder result = orderService.markFulfilled(ORDER_ID);
        assertEquals("fulfilled", result.getStatus());
    }

    // 34. create 黑名单供应商
    @Test
    void create_blacklistSupplier_throws409() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCode("PO-100");
        req.setSupplierId(SUPPLIER_ID);
        req.setSourceType("direct");
        req.setOrderDate(LocalDate.now());
        OrderItemDto it = new OrderItemDto();
        it.setItemName("X");
        it.setQuantity(1);
        it.setUnitPrice(new BigDecimal("10"));
        req.setItems(List.of(it));
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier("blacklist"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.create(req));
        assertEquals(409, ex.getCode());
    }

    // 35. create sourceType 枚举
    @Test
    void create_invalidSourceType_throws400() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCode("PO-100");
        req.setSupplierId(SUPPLIER_ID);
        req.setSourceType("invalid");
        req.setOrderDate(LocalDate.now());
        OrderItemDto it = new OrderItemDto();
        it.setItemName("X");
        it.setQuantity(1);
        it.setUnitPrice(new BigDecimal("10"));
        req.setItems(List.of(it));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 36. create sourceType=quotation 必须有 sourceId
    @Test
    void create_quotationMissingSourceId_throws400() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCode("PO-100");
        req.setSupplierId(SUPPLIER_ID);
        req.setSourceType("quotation");
        req.setSourceId(null);
        req.setOrderDate(LocalDate.now());
        OrderItemDto it = new OrderItemDto();
        it.setItemName("X");
        it.setQuantity(1);
        it.setUnitPrice(new BigDecimal("10"));
        req.setItems(List.of(it));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> orderService.create(req));
        assertEquals(400, ex.getCode());
    }
}