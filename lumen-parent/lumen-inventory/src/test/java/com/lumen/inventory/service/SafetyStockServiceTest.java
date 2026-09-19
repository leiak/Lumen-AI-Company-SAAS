package com.lumen.inventory.service;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.entity.InvSafetyStock;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.mapper.InvSafetyStockMapper;
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
 * SafetyStock 阈值判断: out_of_stock / low / normal / overstock。
 * 目标: 5 个测试覆盖阈值分级。
 */
@ExtendWith(MockitoExtension.class)
class SafetyStockServiceTest {

    @Mock private InvSafetyStockMapper safetyStockMapper;
    @Mock private InvStockMapper stockMapper;
    @InjectMocks private SafetyStockService safetyStockService;

    private static final long TENANT = 1L;
    private static final long WH_ID = 100L;
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

    private InvSafetyStock rule(BigDecimal min, BigDecimal max) {
        InvSafetyStock r = new InvSafetyStock();
        r.setId(1L);
        r.setTenantId(TENANT);
        r.setWarehouseId(WH_ID);
        r.setItemId(ITEM_ID);
        r.setMinQuantity(min);
        r.setMaxQuantity(max);
        r.setCurrentQuantity(BigDecimal.ZERO);
        r.setAlertStatus(SafetyStockService.ALERT_NORMAL);
        return r;
    }

    private InvStock stock(BigDecimal qty) {
        InvStock s = new InvStock();
        s.setId(1L);
        s.setTenantId(TENANT);
        s.setWarehouseId(WH_ID);
        s.setItemId(ITEM_ID);
        s.setBatchNo("B1");
        s.setQuantity(qty);
        s.setAvailableQuantity(qty);
        s.setLastInAt(LocalDateTime.now());
        return s;
    }

    // 1. current=0 → out_of_stock
    @Test
    void check_currentZero_marksOutOfStock() {
        InvSafetyStock r = rule(new BigDecimal("5"), new BigDecimal("20"));
        when(safetyStockMapper.selectList(any())).thenReturn(List.of(r));
        when(stockMapper.selectList(any())).thenReturn(List.of());
        InvSafetyStock out = safetyStockService.check(WH_ID, ITEM_ID);
        assertEquals(SafetyStockService.ALERT_OUT_OF_STOCK, out.getAlertStatus());
        assertNotNull(out.getLastAlertAt());
    }

    // 2. 0 < current < min → low
    @Test
    void check_currentBelowMin_marksLow() {
        InvSafetyStock r = rule(new BigDecimal("10"), new BigDecimal("100"));
        when(safetyStockMapper.selectList(any())).thenReturn(List.of(r));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(new BigDecimal("3"))));
        InvSafetyStock out = safetyStockService.check(WH_ID, ITEM_ID);
        assertEquals(SafetyStockService.ALERT_LOW, out.getAlertStatus());
        assertNotNull(out.getLastAlertAt());
    }

    // 3. min <= current <= max → normal (无 last_alert_at 变化)
    @Test
    void check_currentInRange_marksNormal() {
        InvSafetyStock r = rule(new BigDecimal("5"), new BigDecimal("20"));
        r.setLastAlertAt(null);
        when(safetyStockMapper.selectList(any())).thenReturn(List.of(r));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(new BigDecimal("10"))));
        InvSafetyStock out = safetyStockService.check(WH_ID, ITEM_ID);
        assertEquals(SafetyStockService.ALERT_NORMAL, out.getAlertStatus());
        assertNull(out.getLastAlertAt());
    }

    // 4. current > max → overstock
    @Test
    void check_currentAboveMax_marksOverstock() {
        InvSafetyStock r = rule(new BigDecimal("5"), new BigDecimal("20"));
        when(safetyStockMapper.selectList(any())).thenReturn(List.of(r));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(new BigDecimal("50"))));
        InvSafetyStock out = safetyStockService.check(WH_ID, ITEM_ID);
        assertEquals(SafetyStockService.ALERT_OVERSTOCK, out.getAlertStatus());
        assertNotNull(out.getLastAlertAt());
    }

    // 5. 缺规则 → null (不抛异常)
    @Test
    void check_noRule_returnsNull() {
        when(safetyStockMapper.selectList(any())).thenReturn(List.of());
        InvSafetyStock out = safetyStockService.check(WH_ID, ITEM_ID);
        assertNull(out);
    }
}