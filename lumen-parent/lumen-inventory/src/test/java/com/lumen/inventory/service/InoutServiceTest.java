package com.lumen.inventory.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.CreateInoutRequest;
import com.lumen.inventory.dto.InoutItemDto;
import com.lumen.inventory.entity.InvInout;
import com.lumen.inventory.entity.InvInoutItem;
import com.lumen.inventory.mapper.InvInoutItemMapper;
import com.lumen.inventory.mapper.InvInoutMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Inout 状态机 + confirm 按 type 路由。
 * 目标: 6 个测试覆盖状态机、类型校验、跨租户、confirm 路由。
 */
@ExtendWith(MockitoExtension.class)
class InoutServiceTest {

    @Mock private InvInoutMapper inoutMapper;
    @Mock private InvInoutItemMapper inoutItemMapper;
    @Mock private StockService stockService;
    @InjectMocks private InoutService inoutService;

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long INOUT_ID = 500L;
    private static final long WAREHOUSE_ID = 100L;
    private static final long ITEM_ID = 200L;
    private static final long USER = 10L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(Set.of("inventory_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private InvInout stub(String status, long tenant) {
        InvInout o = new InvInout();
        o.setId(INOUT_ID);
        o.setTenantId(tenant);
        o.setCode("IN-001");
        o.setType(InoutService.TYPE_IN);
        o.setSourceType(InoutService.SOURCE_PURCHASE);
        o.setWarehouseId(WAREHOUSE_ID);
        o.setStatus(status);
        o.setInoutDate(LocalDate.now());
        return o;
    }

    private InvInoutItem stubItem() {
        InvInoutItem it = new InvInoutItem();
        it.setId(1L);
        it.setInoutId(INOUT_ID);
        it.setItemId(ITEM_ID);
        it.setBatchNo("BATCH-001");
        it.setQuantity(new BigDecimal("10"));
        it.setUnitPrice(new BigDecimal("5.00"));
        it.setSubtotal(new BigDecimal("50.00"));
        it.setTenantId(TENANT);
        return it;
    }

    // 1. confirm type=in → addStock
    @Test
    void confirm_typeIn_callsAddStock() {
        InvInout o = stub(InoutService.STATUS_DRAFT, TENANT);
        o.setType(InoutService.TYPE_IN);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        when(inoutItemMapper.findByInout(eq(TENANT), eq(INOUT_ID))).thenReturn(List.of(stubItem()));
        InvInout out = inoutService.confirm(INOUT_ID);
        assertEquals(InoutService.STATUS_CONFIRMED, out.getStatus());
        verify(stockService, times(1)).addStock(eq(WAREHOUSE_ID), eq(0L), eq(ITEM_ID),
            eq("BATCH-001"), eq(new BigDecimal("10")));
    }

    // 2. confirm type=out → consumeFifo
    @Test
    void confirm_typeOut_callsConsumeFifo() {
        InvInout o = stub(InoutService.STATUS_DRAFT, TENANT);
        o.setType(InoutService.TYPE_OUT);
        InvInoutItem it = stubItem();
        it.setBatchNo(null); // 触发 FIFO
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        when(inoutItemMapper.findByInout(eq(TENANT), eq(INOUT_ID))).thenReturn(List.of(it));
        InvInout out = inoutService.confirm(INOUT_ID);
        assertEquals(InoutService.STATUS_CONFIRMED, out.getStatus());
        verify(stockService, times(1)).consumeFifo(eq(ITEM_ID), eq(WAREHOUSE_ID),
            eq(new BigDecimal("10")));
    }

    // 3. confirm 已 confirmed → 409
    @Test
    void confirm_alreadyConfirmed_throws409() {
        InvInout o = stub(InoutService.STATUS_CONFIRMED, TENANT);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class, () -> inoutService.confirm(INOUT_ID));
        assertEquals(409, ex.getCode());
    }

    // 4. cancel 已 confirmed → 409 (安全要求 #5)
    @Test
    void cancel_alreadyConfirmed_throws409() {
        InvInout o = stub(InoutService.STATUS_CONFIRMED, TENANT);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> inoutService.cancel(INOUT_ID, "no"));
        assertEquals(409, ex.getCode());
    }

    // 5. cancel draft → cancelled
    @Test
    void cancel_draft_succeeds() {
        InvInout o = stub(InoutService.STATUS_DRAFT, TENANT);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        InvInout out = inoutService.cancel(INOUT_ID, "test");
        assertEquals(InoutService.STATUS_CANCELLED, out.getStatus());
    }

    // 6. 跨租户 → 404
    @Test
    void getById_crossTenant_returns404() {
        InvInout o = stub(InoutService.STATUS_DRAFT, OTHER_TENANT);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class, () -> inoutService.getById(INOUT_ID));
        assertEquals(404, ex.getCode());
    }

    // 7. type=transfer 必须 sourceType=transfer (安全要求 #14)
    @Test
    void create_transferType_wrongSourceType_throws400() {
        CreateInoutRequest req = new CreateInoutRequest();
        req.setType(InoutService.TYPE_TRANSFER);
        req.setSourceType(InoutService.SOURCE_PURCHASE);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setInoutDate(LocalDate.now());
        InoutItemDto dto = new InoutItemDto();
        dto.setItemId(ITEM_ID);
        dto.setQuantity(BigDecimal.ONE);
        req.setItems(List.of(dto));
        ServiceException ex = assertThrows(ServiceException.class, () -> inoutService.create(req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("transfer requires sourceType=transfer"));
    }

    // 8. type 必须 in/out/transfer
    @Test
    void create_invalidType_throws400() {
        CreateInoutRequest req = new CreateInoutRequest();
        req.setType("xxx");
        req.setSourceType(InoutService.SOURCE_MANUAL);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setInoutDate(LocalDate.now());
        InoutItemDto dto = new InoutItemDto();
        dto.setItemId(ITEM_ID);
        dto.setQuantity(BigDecimal.ONE);
        req.setItems(List.of(dto));
        ServiceException ex = assertThrows(ServiceException.class, () -> inoutService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 9. create valid
    @Test
    void create_validRequest_persists() {
        CreateInoutRequest req = new CreateInoutRequest();
        req.setType(InoutService.TYPE_IN);
        req.setSourceType(InoutService.SOURCE_PURCHASE);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setInoutDate(LocalDate.now());
        InoutItemDto dto = new InoutItemDto();
        dto.setItemId(ITEM_ID);
        dto.setQuantity(new BigDecimal("5"));
        dto.setUnitPrice(new BigDecimal("2.50"));
        req.setItems(List.of(dto));
        when(inoutMapper.insert(any(InvInout.class))).thenAnswer(inv -> {
            InvInout arg = inv.getArgument(0);
            arg.setId(INOUT_ID);
            return 1;
        });
        InvInout o = inoutService.create(req);
        assertEquals(INOUT_ID, o.getId());
        assertEquals(InoutService.STATUS_DRAFT, o.getStatus());
        verify(inoutItemMapper, times(1)).insert(any(InvInoutItem.class));
    }

    // 10. confirm type=in with no items → 409
    @Test
    void confirm_noItems_throws409() {
        InvInout o = stub(InoutService.STATUS_DRAFT, TENANT);
        o.setType(InoutService.TYPE_IN);
        when(inoutMapper.selectById(INOUT_ID)).thenReturn(o);
        when(inoutItemMapper.findByInout(eq(TENANT), eq(INOUT_ID))).thenReturn(List.of());
        ServiceException ex = assertThrows(ServiceException.class, () -> inoutService.confirm(INOUT_ID));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("no items"));
    }
}