package com.lumen.assets.service;

import com.lumen.assets.dto.TransferRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstTransfer;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstTransferMapper;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Transfer state machine tests: pending → approved → completed; pending → rejected。
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private AstTransferMapper transferMapper;
    @Mock private AstAssetMapper assetMapper;
    @InjectMocks private TransferService transferService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long ASSET_ID = 100L;
    private static final long TRANSFER_ID = 700L;

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
        a.setDeptId(11L);
        a.setCustodianId(20L);
        a.setCode("A-1");
        return a;
    }

    private AstTransfer transfer(String status) {
        AstTransfer t = new AstTransfer();
        t.setId(TRANSFER_ID);
        t.setTenantId(TENANT);
        t.setAssetId(ASSET_ID);
        t.setFromDeptId(11L);
        t.setFromCustodianId(20L);
        t.setToDeptId(22L);
        t.setToCustodianId(30L);
        t.setStatus(status);
        return t;
    }

    // 1. apply 写入 pending + 快照 from 部门/保管人
    @Test
    void apply_pendingStatusAndSnapsFromDept() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(AssetService.STATUS_IN_USE));
        TransferRequest req = new TransferRequest();
        req.setAssetId(ASSET_ID);
        req.setToDeptId(22L);
        req.setToCustodianId(30L);
        req.setTransferDate(LocalDate.of(2026, 9, 18));
        req.setReason("relocate");
        AstTransfer out = transferService.apply(req);
        ArgumentCaptor<AstTransfer> captor = ArgumentCaptor.forClass(AstTransfer.class);
        verify(transferMapper).insert(captor.capture());
        AstTransfer inserted = captor.getValue();
        assertEquals(TransferService.STATUS_PENDING, inserted.getStatus());
        assertEquals(11L, inserted.getFromDeptId());
        assertEquals(22L, inserted.getToDeptId());
    }

    // 2. approve pending → approved
    @Test
    void approve_pending_succeeds() {
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(TransferService.STATUS_PENDING));
        AstTransfer out = transferService.approve(TRANSFER_ID);
        assertEquals(TransferService.STATUS_APPROVED, out.getStatus());
    }

    // 3. approve 非 pending → 409
    @Test
    void approve_alreadyApproved_rejected() {
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(TransferService.STATUS_APPROVED));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.approve(TRANSFER_ID));
        assertEquals(409, ex.getCode());
    }

    // 4. complete 必须 approved 才能 complete（更新 asset 部门/保管人）
    @Test
    void complete_pending_rejected() {
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(TransferService.STATUS_PENDING));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.complete(TRANSFER_ID));
        assertEquals(409, ex.getCode());
    }

    // 5. complete approved → 更新 asset + 状态 completed
    @Test
    void complete_approved_updatesAsset() {
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(TransferService.STATUS_APPROVED));
        AstAsset a = asset(AssetService.STATUS_IN_USE);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(a);
        AstTransfer out = transferService.complete(TRANSFER_ID);
        assertEquals(TransferService.STATUS_COMPLETED, out.getStatus());
        ArgumentCaptor<AstAsset> captor = ArgumentCaptor.forClass(AstAsset.class);
        verify(assetMapper).updateById(captor.capture());
        assertEquals(22L, captor.getValue().getDeptId());
        assertEquals(30L, captor.getValue().getCustodianId());
    }

    // 6. reject pending → rejected
    @Test
    void reject_pending_succeeds() {
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(TransferService.STATUS_PENDING));
        AstTransfer out = transferService.reject(TRANSFER_ID, "no budget");
        assertEquals(TransferService.STATUS_REJECTED, out.getStatus());
        assertTrue(out.getReason().contains("reject"));
    }

    // 7. 报废资产不能 transfer
    @Test
    void apply_scrappedAsset_rejected() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(AssetService.STATUS_SCRAPPED));
        TransferRequest req = new TransferRequest();
        req.setAssetId(ASSET_ID);
        req.setToDeptId(22L);
        req.setToCustodianId(30L);
        ServiceException ex = assertThrows(ServiceException.class, () -> transferService.apply(req));
        assertEquals(409, ex.getCode());
    }
}