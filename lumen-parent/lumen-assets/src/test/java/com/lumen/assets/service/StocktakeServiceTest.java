package com.lumen.assets.service;

import com.lumen.assets.dto.StocktakeItemRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstStocktake;
import com.lumen.assets.entity.AstStocktakeItem;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstStocktakeItemMapper;
import com.lumen.assets.mapper.AstStocktakeMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stocktake state machine + submit item diff + complete requires all submitted.
 */
@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {

    @Mock private AstStocktakeMapper stocktakeMapper;
    @Mock private AstStocktakeItemMapper stocktakeItemMapper;
    @Mock private AstAssetMapper assetMapper;
    @InjectMocks private StocktakeService stocktakeService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long STOCKTAKE_ID = 800L;
    private static final long ITEM_ID = 801L;
    private static final long ASSET_ID = 100L;
    private static final long DEPT_ID = 5L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(java.util.Set.of("assets_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private AstStocktake parent(String status) {
        AstStocktake s = new AstStocktake();
        s.setId(STOCKTAKE_ID);
        s.setTenantId(TENANT);
        s.setCode("ST-2026-09");
        s.setPeriod("2026-09");
        s.setDepartmentId(DEPT_ID);
        s.setStatus(status);
        s.setDiffCount(0);
        return s;
    }

    private AstStocktakeItem item(String expected, String actual) {
        AstStocktakeItem i = new AstStocktakeItem();
        i.setId(ITEM_ID);
        i.setTenantId(TENANT);
        i.setStocktakeId(STOCKTAKE_ID);
        i.setAssetId(ASSET_ID);
        i.setExpectedStatus(expected);
        i.setActualStatus(actual);
        i.setDiffType(StocktakeService.DIFF_NONE);
        return i;
    }

    // 1. submitItem 状态相同 → diff_type = none
    @Test
    void submitItem_sameStatus_diffNone() {
        AstStocktakeItem i = item(AssetService.STATUS_IN_USE, null);
        when(stocktakeItemMapper.selectById(ITEM_ID)).thenReturn(i);
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));

        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ID);
        req.setActualStatus(AssetService.STATUS_IN_USE);
        AstStocktakeItem out = stocktakeService.submitItem(req);
        assertEquals(StocktakeService.DIFF_NONE, out.getDiffType());
    }

    // 2. submitItem 实际 scrapped → diff = damaged
    @Test
    void submitItem_actualScrapped_diffDamaged() {
        AstStocktakeItem i = item(AssetService.STATUS_IN_USE, null);
        when(stocktakeItemMapper.selectById(ITEM_ID)).thenReturn(i);
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));

        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ID);
        req.setActualStatus(AssetService.STATUS_SCRAPPED);
        AstStocktakeItem out = stocktakeService.submitItem(req);
        assertEquals(StocktakeService.DIFF_DAMAGED, out.getDiffType());
    }

    // 3. submitItem 跨租户 → 404
    @Test
    void submitItem_crossTenant_returns404() {
        AstStocktakeItem i = item(AssetService.STATUS_IN_USE, null);
        i.setTenantId(99L);
        when(stocktakeItemMapper.selectById(ITEM_ID)).thenReturn(i);
        StocktakeItemRequest req = new StocktakeItemRequest();
        req.setItemId(ITEM_ID);
        req.setActualStatus(AssetService.STATUS_IN_USE);
        ServiceException ex = assertThrows(ServiceException.class, () -> stocktakeService.submitItem(req));
        assertEquals(404, ex.getCode());
    }

    // 4. complete 必须所有 item 已 submit
    @Test
    void complete_pendingItems_rejected() {
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));
        AstStocktakeItem submitted = item(AssetService.STATUS_IN_USE, AssetService.STATUS_IN_USE);
        AstStocktakeItem unsubmitted = item(AssetService.STATUS_IN_USE, null);
        when(stocktakeItemMapper.findByStocktake(STOCKTAKE_ID)).thenReturn(List.of(submitted, unsubmitted));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> stocktakeService.complete(STOCKTAKE_ID));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("must be submitted"));
    }

    // 5. complete 全部 submit → 汇总 diff + 更新状态
    @Test
    void complete_allSubmitted_summarizesDiff() {
        when(stocktakeMapper.selectById(STOCKTAKE_ID)).thenReturn(parent(StocktakeService.STATUS_IN_PROGRESS));
        AstStocktakeItem ok = item(AssetService.STATUS_IN_USE, AssetService.STATUS_IN_USE); // diff none
        AstStocktakeItem damaged = item(AssetService.STATUS_IN_USE, AssetService.STATUS_SCRAPPED);
        damaged.setDiffType(StocktakeService.DIFF_DAMAGED);
        when(stocktakeItemMapper.findByStocktake(STOCKTAKE_ID)).thenReturn(List.of(ok, damaged));

        AstStocktake out = stocktakeService.complete(STOCKTAKE_ID);
        assertEquals(StocktakeService.STATUS_COMPLETED, out.getStatus());
        assertEquals(1, out.getDiffCount());
        assertNotNull(out.getCompletedAt());
    }

    // 6. start 创建 stocktake + snapshot 资产
    @Test
    void start_createsSnapshotItems() {
        AstAsset a = new AstAsset();
        a.setId(ASSET_ID);
        a.setTenantId(TENANT);
        a.setDeptId(DEPT_ID);
        a.setStatus(AssetService.STATUS_IN_USE);
        when(assetMapper.findByDeptAndStatus(eq(DEPT_ID), any())).thenReturn(null);
        when(assetMapper.selectList(any())).thenReturn(List.of(a));

        AstStocktake s = stocktakeService.start("ST-X", "2026-09", DEPT_ID,
            LocalDateTime.of(2026, 9, 1, 0, 0));
        assertEquals(StocktakeService.STATUS_IN_PROGRESS, s.getStatus());
        verify(stocktakeItemMapper, org.mockito.Mockito.times(1)).insert(any(AstStocktakeItem.class));
    }
}