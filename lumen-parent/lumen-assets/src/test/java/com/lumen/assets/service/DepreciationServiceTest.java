package com.lumen.assets.service;

import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstDepreciation;
import com.lumen.assets.mapper.AstAssetMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Depreciation monthly batch — idempotent + BigDecimal.
 */
@ExtendWith(MockitoExtension.class)
class DepreciationServiceTest {

    @Mock private AstAssetMapper assetMapper;
    @Mock private AstDepreciationMapper depreciationMapper;
    @Mock private AssetService assetService;
    @InjectMocks private DepreciationService depreciationService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long ASSET_ID = 100L;

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

    private AstAsset inUseAsset() {
        AstAsset a = new AstAsset();
        a.setId(ASSET_ID);
        a.setTenantId(TENANT);
        a.setCode("A-1");
        a.setOriginalValue(new BigDecimal("12000.00"));
        a.setSalvageValue(BigDecimal.ZERO);
        a.setUsefulLifeMonths(36);
        a.setDepreciationMethod(AssetService.METHOD_STRAIGHT_LINE);
        a.setPurchaseDate(LocalDate.of(2024, 1, 1));
        a.setStatus(AssetService.STATUS_IN_USE);
        return a;
    }

    // 1. period 格式错误 → 400
    @Test
    void runMonthly_invalidPeriod_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> depreciationService.runMonthly("2026/09"));
        assertEquals(400, ex.getCode());
    }

    // 2. 没有 in_use 资产 → 空结果
    @Test
    void runMonthly_noAssets_returnsEmpty() {
        when(assetMapper.selectList(any())).thenReturn(List.of());
        List<AstDepreciation> out = depreciationService.runMonthly("2026-09");
        assertTrue(out.isEmpty());
    }

    // 3. 单资产已计提过 → 跳过
    @Test
    void runMonthly_skipsAlreadyDepreciated() {
        when(assetMapper.selectList(any())).thenReturn(List.of(inUseAsset()));
        AstDepreciation existing = new AstDepreciation();
        existing.setAssetId(ASSET_ID);
        existing.setPeriod("2026-09");
        when(depreciationMapper.findByAssetAndPeriod(ASSET_ID, "2026-09")).thenReturn(existing);
        List<AstDepreciation> out = depreciationService.runMonthly("2026-09");
        assertTrue(out.isEmpty());
        verify(assetService, never()).depreciateOne(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // 4. 单资产未计提 → 调用 assetService.depreciateOne
    @Test
    void runMonthly_invokesAssetServiceForFreshPeriod() {
        when(assetMapper.selectList(any())).thenReturn(List.of(inUseAsset()));
        when(depreciationMapper.findByAssetAndPeriod(ASSET_ID, "2026-09")).thenReturn(null);
        AstDepreciation dep = new AstDepreciation();
        dep.setAssetId(ASSET_ID);
        dep.setPeriod("2026-09");
        when(assetService.depreciateOne(eq(ASSET_ID), eq("2026-09"), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(dep);
        List<AstDepreciation> out = depreciationService.runMonthly("2026-09");
        assertEquals(1, out.size());
    }

    // 5. 缺失 tenant context → 401
    @Test
    void runMonthly_missingContext_returns401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> depreciationService.runMonthly("2026-09"));
        assertEquals(401, ex.getCode());
    }
}