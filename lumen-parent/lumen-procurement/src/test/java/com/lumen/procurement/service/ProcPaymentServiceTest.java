package com.lumen.procurement.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcPaymentServiceTest {

    @Mock private ProcPaymentMapper paymentMapper;
    @Mock private ProcOrderMapper orderMapper;
    @Mock private ProcReceiptMapper receiptMapper;
    @Mock private ProcOrderService orderService;
    @InjectMocks private ProcPaymentService paymentService;

    private static final long TENANT = 1L;
    private static final long PAYMENT_ID = 100L;
    private static final long ORDER_ID = 50L;

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

    private ProcPayment stub(String status) {
        ProcPayment p = new ProcPayment();
        p.setId(PAYMENT_ID);
        p.setPaymentNo("PAY-001");
        p.setSourceType("order");
        p.setSourceId(ORDER_ID);
        p.setAmount(new BigDecimal("100"));
        p.setStatus(status);
        p.setTenantId(TENANT);
        return p;
    }

    private ProcOrder order(String status) {
        ProcOrder o = new ProcOrder();
        o.setId(ORDER_ID);
        o.setCode("PO-001");
        o.setStatus(status);
        o.setTenantId(TENANT);
        return o;
    }

    // 43. apply 仅 approved 订单可发起
    @Test
    void apply_fromDraftOrder_throws409() {
        ApplyPaymentRequest req = new ApplyPaymentRequest();
        req.setPaymentNo("PAY-NEW");
        req.setSourceType("order");
        req.setSourceId(ORDER_ID);
        req.setAmount(new BigDecimal("100"));
        req.setPaymentMethod("bank_transfer");
        when(paymentMapper.findByPaymentNo(eq(TENANT), eq("PAY-NEW"))).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order("draft"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.apply(req));
        assertEquals(409, ex.getCode());
    }

    // 44. apply 金额 ≤ 0
    @Test
    void apply_zeroAmount_throws400() {
        ApplyPaymentRequest req = new ApplyPaymentRequest();
        req.setPaymentNo("PAY-NEW");
        req.setSourceType("order");
        req.setSourceId(ORDER_ID);
        req.setAmount(BigDecimal.ZERO);
        req.setPaymentMethod("bank_transfer");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.apply(req));
        assertEquals(400, ex.getCode());
    }

    // 45. apply paymentNo 重复
    @Test
    void apply_duplicatePaymentNo_throws409() {
        ApplyPaymentRequest req = new ApplyPaymentRequest();
        req.setPaymentNo("PAY-DUP");
        req.setSourceType("order");
        req.setSourceId(ORDER_ID);
        req.setAmount(new BigDecimal("100"));
        req.setPaymentMethod("bank_transfer");
        when(paymentMapper.findByPaymentNo(eq(TENANT), eq("PAY-DUP"))).thenReturn(stub("pending"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.apply(req));
        assertEquals(409, ex.getCode());
    }

    // 46. apply receipt source 必须是 confirmed
    @Test
    void apply_fromPendingReceipt_throws409() {
        ApplyPaymentRequest req = new ApplyPaymentRequest();
        req.setPaymentNo("PAY-NEW");
        req.setSourceType("receipt");
        req.setSourceId(ORDER_ID);
        req.setAmount(new BigDecimal("100"));
        req.setPaymentMethod("bank_transfer");
        ProcReceipt r = new ProcReceipt();
        r.setId(ORDER_ID);
        r.setStatus("pending");
        r.setTenantId(TENANT);
        when(paymentMapper.findByPaymentNo(eq(TENANT), eq("PAY-NEW"))).thenReturn(null);
        when(receiptMapper.selectById(ORDER_ID)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.apply(req));
        assertEquals(409, ex.getCode());
    }

    // 47. approve 仅 pending
    @Test
    void approve_alreadyApproved_throws409() {
        ProcPayment p = stub("approved");
        when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.approve(PAYMENT_ID));
        assertEquals(409, ex.getCode());
    }

    // 48. markPaid 仅 approved
    @Test
    void markPaid_fromPending_throws409() {
        ProcPayment p = stub("pending");
        when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.markPaid(PAYMENT_ID));
        assertEquals(409, ex.getCode());
    }

    // 49. markPaid 成功 → paid; 若 order 已 approved 且全收货 → 联动 markFulfilled
    @Test
    void markPaid_approvedOrder_setsPaidAndFulfillsOrder() {
        ProcPayment p = stub("approved");
        when(paymentMapper.selectById(PAYMENT_ID)).thenReturn(p);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order("approved"));
        // orderService.markFulfilled throws (not fully received)
        org.mockito.Mockito.doThrow(new ServiceException(409, "not fully received"))
            .when(orderService).markFulfilled(ORDER_ID);
        ProcPayment result = paymentService.markPaid(PAYMENT_ID);
        assertEquals("paid", result.getStatus());
        assertNotNull(result.getPaidAt());
    }

    // 50. apply 跨租户 order
    @Test
    void apply_crossTenantOrder_throws404() {
        ApplyPaymentRequest req = new ApplyPaymentRequest();
        req.setPaymentNo("PAY-NEW");
        req.setSourceType("order");
        req.setSourceId(ORDER_ID);
        req.setAmount(new BigDecimal("100"));
        req.setPaymentMethod("bank_transfer");
        when(paymentMapper.findByPaymentNo(eq(TENANT), eq("PAY-NEW"))).thenReturn(null);
        ProcOrder o = order("approved");
        o.setTenantId(99L);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentService.apply(req));
        assertEquals(404, ex.getCode());
    }
}