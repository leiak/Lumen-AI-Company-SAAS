package com.lumen.inventory.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.StartStocktakeRequest;
import com.lumen.inventory.dto.StocktakeItemRequest;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.entity.InvStocktake;
import com.lumen.inventory.entity.InvStocktakeItem;
import com.lumen.inventory.mapper.InvStockMapper;
import com.lumen.inventory.mapper.InvStocktakeItemMapper;
import com.lumen.inventory.mapper.InvStocktakeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 盘点单: start snapshot, submitItem diff 计算, complete 必须所有已 submit。
 * 目标: 6 个测试覆盖差异汇总、跨租户、状态机。
 */
@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {

    @Mock private InvStocktakeMapper stocktakeMapper;
    @Mock private InvStocktakeItemMapper stocktakeItemMapper;
    @Mock private InvStockMapper stockMapper;
    @InjectMocks private StocktakeService stocktakeService;

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long STOCKTAKE_ID = 700L;
    private static final long WH_ID = 100L;
    private static final long ITEM_ID = 200L;
    private static final long ITEM_ROW_ID = 800L;
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

    private InvStocktake parent(String status) {
        InvStocktake s = new InvStocktake();
        s.setId(STOCKTAKE_ID);
        s.setTenantId(TENANT);
        s.setCode("ST-001");
        s.setWarehouseId(WH_ID);
        s.setPeriod("2026-09");
        s.setStatus(status);
        s.setDiffCount(0);
        return s;
    }

    private InvStocktakeItem row(BigDecimal sys, BigDecimal actual, Integer submitted, long tenant) {
        InvStocktakeItem i = new InvStocktakeItem();
        i.setId(ITEM_ROW_ID);
        i.setTenantId(tenant);
        i.setStocktakeId(STOCKTAKE_ID);
        i.setItemId(ITEM_ID);
        i.setWarehouseId(WH_ID);
        i.setBatchNo("B1");
        i.setSystemQuantity(sys);
        i.setActualQuantity(actual);
        i.setDiffQuantity(actual == null ? null : actual.subtract(sys == null ? BigDecimal.ZERO : sys));
        i.setSubmitted(submitted);
        return i;
    }

    // 1. submitItem 录入 actual → diff = actual - sys
    @Test
    void submitItem_calculatesDiff() {
        InvStocktakeItem row = row(new BigDecimal("10"), null, 0, TENANT);
        when(stocktakeItemMapper.selectById(ITEM_ROW_ID)).thenReturn(row);
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));

        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ROW_ID);
        req.setActualQuantity(new BigDecimal("8"));
        InvStocktakeItem out = stocktakeService.submitItem(req);
        assertEquals(new BigDecimal("-2"), out.getDiffQuantity());
        assertEquals(1, out.getSubmitted());
    }

    // 2. submitItem 跨租户 → 404
    @Test
    void submitItem_crossTenant_returns404() {
        InvStocktakeItem row = row(new BigDecimal("10"), null, 0, OTHER_TENANT);
        when(stocktakeItemMapper.selectById(ITEM_ROW_ID)).thenReturn(row);
        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ROW_ID);
        req.setActualQuantity(BigDecimal.ONE);
        ServiceException ex = assertThrows(ServiceException.class, () -> stocktakeService.submitItem(req));
        assertEquals(404, ex.getCode());
    }

    // 3. complete 必须所有 item 已 submit (安全要求 #9)
    @Test
    void complete_pendingItems_throws409() {
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));
        InvStocktakeItem submitted = row(new BigDecimal("10"), new BigDecimal("10"), 1, TENANT);
        InvStocktakeItem pending = row(new BigDecimal("10"), null, 0, TENANT);
        when(stocktakeItemMapper.findByStocktake(eq(TENANT), eq(STOCKTAKE_ID)))
            .thenReturn(List.of(submitted, pending));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> stocktakeService.complete(STOCKTAKE_ID));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("must be submitted"));
    }

    // 4. complete 全部 submit → diff_count 汇总非零项
    @Test
    void complete_summarizesDiffCount() {
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));
        InvStocktakeItem ok = row(new BigDecimal("10"), new BigDecimal("10"), 1, TENANT);
        ok.setDiffQuantity(BigDecimal.ZERO);
        InvStocktakeItem diff = row(new BigDecimal("10"), new BigDecimal("8"), 1, TENANT);
        diff.setDiffQuantity(new BigDecimal("-2"));
        when(stocktakeItemMapper.findByStocktake(eq(TENANT), eq(STOCKTAKE_ID)))
            .thenReturn(List.of(ok, diff));
        InvStocktake out = stocktakeService.complete(STOCKTAKE_ID);
        assertEquals(StocktakeService.STATUS_COMPLETED, out.getStatus());
        assertEquals(1, out.getDiffCount());
        assertNotNull(out.getCompletedAt());
    }

    // 5. start → snapshot 库存 → items
    @Test
    void start_createsSnapshotItems() {
        InvStock stk = new InvStock();
        stk.setId(1L);
        stk.setTenantId(TENANT);
        stk.setWarehouseId(WH_ID);
        stk.setItemId(ITEM_ID);
        stk.setBatchNo("B1");
        stk.setQuantity(new BigDecimal("10"));
        when(stockMapper.selectList(any())).thenReturn(List.of(stk));
        StartStocktakeRequest req = new StartStocktakeRequest();
        req.setCode("ST-002");
        req.setWarehouseId(WH_ID);
        req.setPeriod("2026-09");
        InvStocktake out = stocktakeService.start(req);
        assertEquals(StocktakeService.STATUS_IN_PROGRESS, out.getStatus());
        verify(stocktakeItemMapper, times(1)).insert(any(InvStocktakeItem.class));
    }

    // 6. start 缺 code → 400
    @Test
    void start_missingCode_throws400() {
        StartStocktakeRequest req = new StartStocktakeRequest();
        req.setWarehouseId(WH_ID);
        req.setPeriod("2026-09");
        ServiceException ex = assertThrows(ServiceException.class, () -> stocktakeService.start(req));
        assertEquals(400, ex.getCode());
    }

    // 7. submitItem 已 completed → 409
    @Test
    void submitItem_alreadyCompleted_throws409() {
        InvStocktakeItem row = row(new BigDecimal("10"), null, 0, TENANT);
        when(stocktakeItemMapper.selectById(ITEM_ROW_ID)).thenReturn(row);
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_COMPLETED));
        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ROW_ID);
        req.setActualQuantity(BigDecimal.ONE);
        ServiceException ex = assertThrows(ServiceException.class, () -> stocktakeService.submitItem(req));
        assertEquals(409, ex.getCode());
    }
}