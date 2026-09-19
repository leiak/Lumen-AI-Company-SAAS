package com.lumen.inventory.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.StockDeductionDto;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.mapper.InvStockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FIFO 顺序扣减 + addStock/consume/lock/unlock 安全校验。
 * 目标: 6 个测试覆盖 FIFO 顺序、安全要求 #4/#11。
 */
@ExtendWith(MockitoExtension.class)
class StockServiceFifoTest {

    @Mock private InvStockMapper stockMapper;
    @InjectMocks private StockService stockService;

    private static final long TENANT = 1L;
    private static final long WH_ID = 100L;
    private static final long LOC_ID = 50L;
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

    private InvStock stock(long id, String batch, BigDecimal qty, LocalDateTime lastInAt) {
        InvStock s = new InvStock();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setWarehouseId(WH_ID);
        s.setLocationId(LOC_ID);
        s.setItemId(ITEM_ID);
        s.setBatchNo(batch);
        s.setQuantity(qty);
        s.setAvailableQuantity(qty);
        s.setLockedQuantity(BigDecimal.ZERO);
        s.setLastInAt(lastInAt);
        return s;
    }

    // 1. FIFO 按 last_in_at ASC 顺序扣减 (安全要求 #8)
    @Test
    void consumeFifo_deductsInOrder() {
        InvStock oldest = stock(1L, "BATCH-OLD",
            new BigDecimal("3"), LocalDateTime.of(2026, 1, 1, 0, 0));
        InvStock middle = stock(2L, "BATCH-MID",
            new BigDecimal("5"), LocalDateTime.of(2026, 6, 1, 0, 0));
        InvStock newest = stock(3L, "BATCH-NEW",
            new BigDecimal("10"), LocalDateTime.of(2026, 9, 1, 0, 0));
        when(stockMapper.findByWarehouseAndItem(eq(TENANT), eq(WH_ID), eq(ITEM_ID)))
            .thenReturn(List.of(oldest, middle, newest));

        List<StockDeductionDto> out = stockService.consumeFifo(ITEM_ID, WH_ID, new BigDecimal("6"));
        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getStockId());
        assertEquals(new BigDecimal("3"), out.get(0).getDeducted());
        assertEquals(2L, out.get(1).getStockId());
        assertEquals(new BigDecimal("3"), out.get(1).getDeducted());
        verify(stockMapper, times(2)).updateById(any(InvStock.class));
    }

    // 2. addStock quantity < 0 → 400 (安全要求 #4)
    @Test
    void addStock_negative_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> stockService.addStock(WH_ID, LOC_ID, ITEM_ID, "B1", new BigDecimal("-1")));
        assertEquals(400, ex.getCode());
    }

    // 3. consume available < quantity → 409 (安全要求 #4)
    @Test
    void consume_insufficient_throws409() {
        InvStock s = stock(1L, "B1", new BigDecimal("2"), LocalDateTime.now());
        when(stockMapper.selectById(1L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> stockService.consume(1L, new BigDecimal("5")));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("Insufficient"));
    }

    // 4. lock/unlock 配对 (安全要求 #11)
    @Test
    void lock_thenUnlock_returnsToAvailable() {
        InvStock s = stock(1L, "B1", new BigDecimal("10"), LocalDateTime.now());
        when(stockMapper.selectById(1L)).thenReturn(s);
        InvStock locked = stockService.lock(1L, new BigDecimal("4"));
        assertEquals(new BigDecimal("6"), locked.getAvailableQuantity());
        assertEquals(new BigDecimal("4"), locked.getLockedQuantity());
        InvStock unlocked = stockService.unlock(1L, new BigDecimal("4"));
        assertEquals(new BigDecimal("10"), unlocked.getAvailableQuantity());
        assertEquals(BigDecimal.ZERO, unlocked.getLockedQuantity());
    }

    // 5. consumeFifo 库存不足 → 409
    @Test
    void consumeFifo_insufficient_throws409() {
        InvStock only = stock(1L, "B1", new BigDecimal("3"), LocalDateTime.of(2026, 1, 1, 0, 0));
        when(stockMapper.findByWarehouseAndItem(eq(TENANT), eq(WH_ID), eq(ITEM_ID)))
            .thenReturn(List.of(only));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> stockService.consumeFifo(ITEM_ID, WH_ID, new BigDecimal("10")));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("Insufficient"));
    }

    // 6. addStock 已存在批次 → 增量更新
    @Test
    void addStock_existingBatch_updatesIncrement() {
        InvStock existing = stock(1L, "B1", new BigDecimal("5"), LocalDateTime.of(2026, 1, 1, 0, 0));
        when(stockMapper.findByBatch(eq(TENANT), eq(WH_ID), eq(LOC_ID), eq(ITEM_ID), eq("B1")))
            .thenReturn(existing);
        InvStock out = stockService.addStock(WH_ID, LOC_ID, ITEM_ID, "B1", new BigDecimal("7"));
        assertEquals(new BigDecimal("12"), out.getQuantity());
        assertEquals(new BigDecimal("12"), out.getAvailableQuantity());
        verify(stockMapper, times(1)).updateById(any(InvStock.class));
        verify(stockMapper, never()).insert(any(InvStock.class));
    }
}