package com.lumen.assets.service;

import com.lumen.assets.dto.ApplyCustodyRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstCustody;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstCustodyMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Custody service tests: 同时只能一人持有；归还状态机。
 */
@ExtendWith(MockitoExtension.class)
class CustodyServiceTest {

    @Mock private AstCustodyMapper custodyMapper;
    @Mock private AstAssetMapper assetMapper;
    @InjectMocks private CustodyService custodyService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long ASSET_ID = 100L;
    private static final long CUSTODY_ID = 500L;

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

    private AstAsset asset(String status) {
        AstAsset a = new AstAsset();
        a.setId(ASSET_ID);
        a.setTenantId(TENANT);
        a.setStatus(status);
        a.setCode("A-1");
        return a;
    }

    // 1. 同时唯一持有：已有 active 记录 → 409
    @Test
    void apply_existingActive_rejected() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(AssetService.STATUS_IN_USE));
        AstCustody active = new AstCustody();
        active.setId(99L);
        active.setAssetId(ASSET_ID);
        active.setStatus(CustodyService.STATUS_ACTIVE);
        when(custodyMapper.findActiveByAsset(ASSET_ID)).thenReturn(active);

        ApplyCustodyRequest req = new ApplyCustodyRequest();
        req.setAssetId(ASSET_ID);
        req.setCustodianId(20L);
        req.setStartAt(LocalDateTime.now());

        ServiceException ex = assertThrows(ServiceException.class, () -> custodyService.apply(req));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("already in custody"));
    }

    // 2. 报废资产不可申请
    @Test
    void apply_scrappedAsset_rejected() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(AssetService.STATUS_SCRAPPED));
        ApplyCustodyRequest req = new ApplyCustodyRequest();
        req.setAssetId(ASSET_ID);
        req.setCustodianId(20L);
        req.setStartAt(LocalDateTime.now());
        ServiceException ex = assertThrows(ServiceException.class, () -> custodyService.apply(req));
        assertEquals(409, ex.getCode());
    }

    // 3. 成功申请 → 写入 record + 同步 asset.custodian
    @Test
    void apply_success_persistsRecordAndUpdatesAsset() {
        AstAsset a = asset(AssetService.STATUS_IN_STOCK);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        when(custodyMapper.findActiveByAsset(ASSET_ID)).thenReturn(null);

        ApplyCustodyRequest req = new ApplyCustodyRequest();
        req.setAssetId(ASSET_ID);
        req.setCustodianId(20L);
        req.setStartAt(LocalDateTime.of(2026, 9, 1, 9, 0));

        AstCustody out = custodyService.apply(req);
        assertNotNull(out);
        assertEquals(CustodyService.STATUS_ACTIVE, out.getStatus());
        ArgumentCaptor<AstAsset> captor = ArgumentCaptor.forClass(AstAsset.class);
        verify(assetMapper).updateById(captor.capture());
        assertEquals(20L, captor.getValue().getCustodianId());
        assertEquals(AssetService.STATUS_IN_USE, captor.getValue().getStatus());
    }

    // 4. 跨租户 asset → 404
    @Test
    void apply_crossTenantAsset_returns404() {
        AstAsset a = asset(AssetService.STATUS_IN_USE);
        a.setTenantId(99L);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        ApplyCustodyRequest req = new ApplyCustodyRequest();
        req.setAssetId(ASSET_ID);
        req.setCustodianId(20L);
        req.setStartAt(LocalDateTime.now());
        ServiceException ex = assertThrows(ServiceException.class, () -> custodyService.apply(req));
        assertEquals(404, ex.getCode());
    }

    // 5. 归还：已归还的再归还 → 409
    @Test
    void returnCustody_alreadyReturned_rejected() {
        AstCustody c = new AstCustody();
        c.setId(CUSTODY_ID);
        c.setTenantId(TENANT);
        c.setAssetId(ASSET_ID);
        c.setStatus(CustodyService.STATUS_RETURNED);
        c.setReturnAt(LocalDateTime.now());
        when(custodyMapper.selectById(CUSTODY_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> custodyService.returnCustody(CUSTODY_ID, LocalDateTime.now()));
        assertEquals(409, ex.getCode());
    }

    // 6. 归还成功
    @Test
    void returnCustody_success_marksReturned() {
        AstCustody c = new AstCustody();
        c.setId(CUSTODY_ID);
        c.setTenantId(TENANT);
        c.setAssetId(ASSET_ID);
        c.setStatus(CustodyService.STATUS_ACTIVE);
        when(custodyMapper.selectById(CUSTODY_ID)).thenReturn(c);
        AstCustody out = custodyService.returnCustody(CUSTODY_ID, LocalDateTime.of(2026, 9, 30, 17, 0));
        assertEquals(CustodyService.STATUS_RETURNED, out.getStatus());
        assertNotNull(out.getReturnAt());
        verify(custodyMapper).updateById(any(AstCustody.class));
    }

    // 7. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstCustody c = new AstCustody();
        c.setId(CUSTODY_ID);
        c.setTenantId(99L);
        c.setAssetId(ASSET_ID);
        when(custodyMapper.selectById(CUSTODY_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> custodyService.getById(CUSTODY_ID));
        assertEquals(404, ex.getCode());
    }
}