package com.lumen.procurement.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcReceiptServiceTest {

    @Mock private ProcReceiptMapper receiptMapper;
    @Mock private ProcOrderMapper orderMapper;
    @Mock private ProcOrderItemMapper orderItemMapper;
    @InjectMocks private ProcReceiptService receiptService;

    private static final long TENANT = 1L;
    private static final long RECEIPT_ID = 100L;
    private static final long ORDER_ID = 50L;
    private static final long ITEM_A = 1L;
    private static final long ITEM_B = 2L;

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

    private ProcReceipt receipt(String status) {
        ProcReceipt r = new ProcReceipt();
        r.setId(RECEIPT_ID);
        r.setCode("RC-001");
        r.setOrderId(ORDER_ID);
        r.setReceiptDate(LocalDate.now());
        r.setStatus(status);
        r.setTenantId(TENANT);
        return r;
    }

    private ProcOrder order() {
        ProcOrder o = new ProcOrder();
        o.setId(ORDER_ID);
        o.setCode("PO-001");
        o.setTenantId(TENANT);
        return o;
    }

    private ProcOrderItem item(long id, int qty, int recv) {
        ProcOrderItem it = new ProcOrderItem();
        it.setId(id);
        it.setOrderId(ORDER_ID);
        it.setItemName("X");
        it.setQuantity(qty);
        it.setUnitPrice(new BigDecimal("10"));
        it.setSubtotal(new BigDecimal(qty * 10));
        it.setReceivedQuantity(recv);
        it.setTenantId(TENANT);
        return it;
    }

    // 37. confirm 全部到货 → confirmed
    @Test
    void confirm_allReceived_setsConfirmed() {
        ProcReceipt r = receipt("pending");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(item(ITEM_A, 10, 0), item(ITEM_B, 5, 0))));

        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        Map<Long, Long> actuals = new HashMap<>();
        actuals.put(ITEM_A, 10L);
        actuals.put(ITEM_B, 5L);
        req.setActualQuantities(actuals);
        ProcReceipt result = receiptService.confirm(RECEIPT_ID, req);
        assertEquals("confirmed", result.getStatus());
    }

    // 38. confirm 部分到货 → discrepancy
    @Test
    void confirm_partialReceived_setsDiscrepancy() {
        ProcReceipt r = receipt("pending");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(item(ITEM_A, 10, 0), item(ITEM_B, 5, 0))));

        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        Map<Long, Long> actuals = new HashMap<>();
        actuals.put(ITEM_A, 10L);
        actuals.put(ITEM_B, 3L);  // 缺 2
        req.setActualQuantities(actuals);
        ProcReceipt result = receiptService.confirm(RECEIPT_ID, req);
        assertEquals("discrepancy", result.getStatus());
    }

    // 39. confirm actualQty > ordered → 409
    @Test
    void confirm_overReceived_throws409() {
        ProcReceipt r = receipt("pending");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(item(ITEM_A, 10, 0))));

        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        Map<Long, Long> actuals = new HashMap<>();
        actuals.put(ITEM_A, 11L);  // 超出
        req.setActualQuantities(actuals);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.confirm(RECEIPT_ID, req));
        assertEquals(409, ex.getCode());
    }

    // 40. confirm 非 pending → 409
    @Test
    void confirm_alreadyConfirmed_throws409() {
        ProcReceipt r = receipt("confirmed");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        req.setActualQuantities(new HashMap<>());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.confirm(RECEIPT_ID, req));
        assertEquals(409, ex.getCode());
    }

    // 41. confirm 空 actuals → 400
    @Test
    void confirm_emptyActuals_throws400() {
        ProcReceipt r = receipt("pending");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        req.setActualQuantities(new HashMap<>());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.confirm(RECEIPT_ID, req));
        assertEquals(400, ex.getCode());
    }

    // 42. confirm 累计超出
    @Test
    void confirm_cumulativeExceedsOrder_throws409() {
        ProcReceipt r = receipt("pending");
        when(receiptMapper.selectById(RECEIPT_ID)).thenReturn(r);
        // 已收到 8, ordered 10, 现在再收 5 → 累计 13 > 10
        when(orderItemMapper.findByOrder(eq(TENANT), eq(ORDER_ID)))
            .thenReturn(new ArrayList<>(List.of(item(ITEM_A, 10, 8))));

        ConfirmReceiptRequest req = new ConfirmReceiptRequest();
        Map<Long, Long> actuals = new HashMap<>();
        actuals.put(ITEM_A, 5L);
        req.setActualQuantities(actuals);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.confirm(RECEIPT_ID, req));
        assertEquals(409, ex.getCode());
    }
}