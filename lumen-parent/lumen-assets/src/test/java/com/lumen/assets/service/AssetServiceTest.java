package com.lumen.assets.service;

import com.lumen.assets.dto.SaveAssetRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstCategory;
import com.lumen.assets.entity.AstDepreciation;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstCategoryMapper;
import com.lumen.assets.mapper.AstDepreciationMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Asset service tests: state machine, depreciation calculation (BigDecimal),
 * cross-tenant 404, originalValue immutability.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AstAssetMapper assetMapper;
    @Mock private AstCategoryMapper categoryMapper;
    @Mock private AstDepreciationMapper depreciationMapper;
    @InjectMocks private AssetService assetService;

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long ASSET_ID = 100L;
    private static final long CATEGORY_ID = 50L;
    private static final long USER = 10L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(new HashSet<>(Set.of("assets_admin"))).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private AstCategory category() {
        AstCategory c = new AstCategory();
        c.setId(CATEGORY_ID);
        c.setTenantId(TENANT);
        c.setCode("IT");
        c.setName("IT");
        c.setLevel(1);
        c.setDepreciationMethodDefault(AssetService.METHOD_STRAIGHT_LINE);
        c.setUsefulLifeDefault(36);
        return c;
    }

    private AstAsset assetWithStatus(String status) {
        AstAsset a = new AstAsset();
        a.setId(ASSET_ID);
        a.setTenantId(TENANT);
        a.setCode("A-001");
        a.setName("Laptop");
        a.setCategoryId(CATEGORY_ID);
        a.setOriginalValue(new BigDecimal("12000.00"));
        a.setCurrentValue(new BigDecimal("10000.00"));
        a.setDepreciationMethod(AssetService.METHOD_STRAIGHT_LINE);
        a.setUsefulLifeMonths(36);
        a.setSalvageValue(BigDecimal.ZERO);
        a.setPurchaseDate(LocalDate.of(2024, 1, 1));
        a.setStatus(status);
        return a;
    }

    // 1. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        a.setTenantId(OTHER_TENANT);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        ServiceException ex = assertThrows(ServiceException.class, () -> assetService.getById(ASSET_ID));
        assertEquals(404, ex.getCode());
    }

    // 2. 缺失 tenant context → 401
    @Test
    void getById_missingTenantContext_returns401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () -> assetService.getById(ASSET_ID));
        assertEquals(401, ex.getCode());
    }

    // 3. 状态机：in_use ↔ maintenance 允许
    @Test
    void update_inUseToMaintenance_allowed() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        SaveAssetRequest req = new SaveAssetRequest();
        req.setStatus(AssetService.STATUS_MAINTENANCE);
        AstAsset result = assetService.update(ASSET_ID, req);
        assertEquals(AssetService.STATUS_MAINTENANCE, result.getStatus());
    }

    // 4. 状态机：scrapped 终态 — update 拒绝
    @Test
    void update_scrappedToInUse_rejected() {
        AstAsset a = assetWithStatus(AssetService.STATUS_SCRAPPED);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        SaveAssetRequest req = new SaveAssetRequest();
        req.setStatus(AssetService.STATUS_IN_USE);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> assetService.update(ASSET_ID, req));
        assertEquals(409, ex.getCode());
    }

    // 5. 报废 → 已报废则再次报废 409
    @Test
    void scrapped_alreadyScrapped_rejected() {
        AstAsset a = assetWithStatus(AssetService.STATUS_SCRAPPED);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> assetService.scrapped(ASSET_ID, "obsolete"));
        assertEquals(409, ex.getCode());
    }

    // 6. 恢复 scrapped 资产被拒绝
    @Test
    void restore_scrapped_rejected() {
        AstAsset a = assetWithStatus(AssetService.STATUS_SCRAPPED);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> assetService.restore(ASSET_ID));
        assertEquals(409, ex.getCode());
    }

    // 7. 原值 immutable：update 不能改 originalValue
    @Test
    void update_originalValueImmutable() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        BigDecimal original = a.getOriginalValue();
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        SaveAssetRequest req = new SaveAssetRequest();
        req.setOriginalValue(new BigDecimal("999999.99")); // 客户端尝试改
        assetService.update(ASSET_ID, req);
        // original 保持不变（service 在 update 体内根本没有拷贝）
        assertEquals(original, a.getOriginalValue());
        assertEquals(0, new BigDecimal("12000.00").compareTo(a.getOriginalValue()));
    }

    // 8. 折旧计算 — 直线法
    @Test
    void straightLine_12months_amount() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        a.setOriginalValue(new BigDecimal("36000.00"));
        a.setSalvageValue(new BigDecimal("0"));
        a.setUsefulLifeMonths(36);
        BigDecimal result = assetService.computeStraightLine(a, 12);
        // (36000 - 0) / 36 = 1000.00；12 个月 = 12000.00
        assertEquals(0, new BigDecimal("12000.00").compareTo(result));
    }

    // 9. 折旧计算 — 末月残值归零
    @Test
    void straightLine_cappedAtDepreciable() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        a.setOriginalValue(new BigDecimal("12000.00"));
        a.setSalvageValue(BigDecimal.ZERO);
        a.setUsefulLifeMonths(36);
        // 远超使用月数，截断在 (12000-0)
        BigDecimal result = assetService.computeStraightLine(a, 999);
        assertEquals(0, new BigDecimal("12000.00").compareTo(result));
    }

    // 10. depreciateOne — 非 in_use 拒绝
    @Test
    void depreciateOne_notInUse_rejected() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_STOCK);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> assetService.depreciateOne(ASSET_ID, "2026-09", 12));
        assertEquals(409, ex.getCode());
    }

    // 11. depreciateOne — period 已计提过 → 409
    @Test
    void depreciateOne_alreadyCalculatedForPeriod_rejected() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        AstDepreciation existing = new AstDepreciation();
        existing.setAssetId(ASSET_ID);
        existing.setPeriod("2026-09");
        when(depreciationMapper.findByAssetAndPeriod(ASSET_ID, "2026-09")).thenReturn(existing);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> assetService.depreciateOne(ASSET_ID, "2026-09", 12));
        assertEquals(409, ex.getCode());
    }

    // 12. depreciateOne — 成功写入 + 更新 currentValue
    @Test
    void depreciateOne_success_writesRowAndUpdatesAsset() {
        AstAsset a = assetWithStatus(AssetService.STATUS_IN_USE);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        when(depreciationMapper.findByAssetAndPeriod(ASSET_ID, "2026-09")).thenReturn(null);
        when(depreciationMapper.selectList(any())).thenReturn(java.util.List.of());
        AstDepreciation result = assetService.depreciateOne(ASSET_ID, "2026-09", 12);
        assertNotNull(result);
        verify(depreciationMapper).insert(any(AstDepreciation.class));
        verify(assetMapper).updateById(any(AstAsset.class));
    }
}