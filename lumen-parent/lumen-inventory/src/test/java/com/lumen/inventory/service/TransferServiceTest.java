package com.lumen.inventory.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.CreateTransferRequest;
import com.lumen.inventory.dto.TransferItemDto;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.entity.InvTransfer;
import com.lumen.inventory.entity.InvTransferItem;
import com.lumen.inventory.mapper.InvTransferItemMapper;
import com.lumen.inventory.mapper.InvTransferMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Transfer 状态机 + ship → in_transit + receive → received。
 * 目标: 6 个测试覆盖状态机、跨租户、ship 必须先于 receive。
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private InvTransferMapper transferMapper;
    @Mock private InvTransferItemMapper transferItemMapper;
    @Mock private StockService stockService;
    @InjectMocks private TransferService transferService;

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long TRANSFER_ID = 600L;
    private static final long FROM_WH = 10L;
    private static final long TO_WH = 20L;
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

    private InvTransfer stub(String status, long tenant) {
        InvTransfer t = new InvTransfer();
        t.setId(TRANSFER_ID);
        t.setTenantId(tenant);
        t.setCode("TF-001");
        t.setFromWarehouseId(FROM_WH);
        t.setToWarehouseId(TO_WH);
        t.setTransferDate(LocalDate.now());
        t.setStatus(status);
        return t;
    }

    private InvTransferItem item(String batch, BigDecimal qty) {
        InvTransferItem it = new InvTransferItem();
        it.setId(1L);
        it.setTransferId(TRANSFER_ID);
        it.setItemId(ITEM_ID);
        it.setBatchNo(batch);
        it.setQuantity(qty);
        it.setTenantId(TENANT);
        return it;
    }

    // 1. ship draft → in_transit, 源仓库 FIFO 扣减
    @Test
    void ship_draft_callsConsumeFifo() {
        InvTransfer t = stub(TransferService.STATUS_DRAFT, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        when(transferItemMapper.findByTransfer(eq(TENANT), eq(TRANSFER_ID)))
            .thenReturn(List.of(item(null, new BigDecimal("5"))));
        InvTransfer out = transferService.ship(TRANSFER_ID);
        assertEquals(TransferService.STATUS_IN_TRANSIT, out.getStatus());
        verify(stockService, times(1)).consumeFifo(eq(ITEM_ID), eq(FROM_WH), eq(new BigDecimal("5")));
    }

    // 2. ship 必须 draft (安全要求 #6)
    @Test
    void ship_alreadyShipped_throws409() {
        InvTransfer t = stub(TransferService.STATUS_IN_TRANSIT, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.ship(TRANSFER_ID));
        assertEquals(409, ex.getCode());
    }

    // 3. receive 必须先 ship (安全要求 #6)
    @Test
    void receive_withoutShip_throws409() {
        InvTransfer t = stub(TransferService.STATUS_DRAFT, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.receive(TRANSFER_ID));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("must ship first"));
    }

    // 4. receive in_transit → received, 目标仓库 addStock
    @Test
    void receive_inTransit_callsAddStock() {
        InvTransfer t = stub(TransferService.STATUS_IN_TRANSIT, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        when(transferItemMapper.findByTransfer(eq(TENANT), eq(TRANSFER_ID)))
            .thenReturn(List.of(item("B1", new BigDecimal("5"))));
        InvTransfer out = transferService.receive(TRANSFER_ID);
        assertEquals(TransferService.STATUS_RECEIVED, out.getStatus());
        verify(stockService, times(1)).addStock(eq(TO_WH), eq(0L), eq(ITEM_ID),
            eq("B1"), eq(new BigDecimal("5")));
    }

    // 5. cancel draft → cancelled
    @Test
    void cancel_draft_succeeds() {
        InvTransfer t = stub(TransferService.STATUS_DRAFT, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        InvTransfer out = transferService.cancel(TRANSFER_ID, "test");
        assertEquals(TransferService.STATUS_CANCELLED, out.getStatus());
    }

    // 6. 跨租户 → 404
    @Test
    void getById_crossTenant_returns404() {
        InvTransfer t = stub(TransferService.STATUS_DRAFT, OTHER_TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        ServiceException ex = assertThrows(ServiceException.class, () -> transferService.getById(TRANSFER_ID));
        assertEquals(404, ex.getCode());
    }

    // 7. from == to → 400
    @Test
    void create_sameWarehouse_throws400() {
        CreateTransferRequest req = new CreateTransferRequest();
        req.setFromWarehouseId(1L);
        req.setToWarehouseId(1L);
        req.setTransferDate(LocalDate.now());
        TransferItemDto it = new TransferItemDto();
        it.setItemId(ITEM_ID);
        it.setQuantity(BigDecimal.ONE);
        req.setItems(List.of(it));
        ServiceException ex = assertThrows(ServiceException.class, () -> transferService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 8. cancel received → 409 (不允许取消)
    @Test
    void cancel_received_throws409() {
        InvTransfer t = stub(TransferService.STATUS_RECEIVED, TENANT);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(t);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.cancel(TRANSFER_ID, "no"));
        assertEquals(409, ex.getCode());
    }
}