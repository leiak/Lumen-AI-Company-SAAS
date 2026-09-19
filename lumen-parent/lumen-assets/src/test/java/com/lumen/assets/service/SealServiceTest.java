package com.lumen.assets.service;

import com.lumen.assets.entity.AstSeal;
import com.lumen.assets.entity.AstSealUsage;
import com.lumen.assets.mapper.AstSealMapper;
import com.lumen.assets.mapper.AstSealUsageMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Seal state machine: use → must return → then can destroy.
 */
@ExtendWith(MockitoExtension.class)
class SealServiceTest {

    @Mock private AstSealMapper sealMapper;
    @Mock private AstSealUsageMapper sealUsageMapper;
    @InjectMocks private SealService sealService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long SEAL_ID = 600L;

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

    private AstSeal seal(String status) {
        AstSeal s = new AstSeal();
        s.setId(SEAL_ID);
        s.setTenantId(TENANT);
        s.setCode("SEAL-001");
        s.setName("公司章");
        s.setSealType(SealService.SEAL_TYPE_COMPANY);
        s.setStatus(status);
        return s;
    }

    // 1. use 在 in_use 印章上 → 写 usage
    @Test
    void use_onInUse_seal_writesUsage() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(seal(SealService.STATUS_IN_USE));
        AstSealUsage out = sealService.use(SEAL_ID, "合同A", "DOC-1", USER, null);
        assertNotNull(out);
        assertEquals(SEAL_ID, out.getSealId());
        assertNull(out.getReturnedAt());
        verify(sealUsageMapper).insert(any(AstSealUsage.class));
    }

    // 2. use destroyed → 409
    @Test
    void use_onDestroyed_rejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(seal(SealService.STATUS_DESTROYED));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> sealService.use(SEAL_ID, "doc", "doc-1", USER, null));
        assertEquals(409, ex.getCode());
    }

    // 3. returnUsage 第一次 OK；第二次 409
    @Test
    void returnUsage_alreadyReturned_rejected() {
        AstSealUsage u = new AstSealUsage();
        u.setId(700L);
        u.setTenantId(TENANT);
        u.setSealId(SEAL_ID);
        u.setReturnedAt(LocalDateTime.now());
        when(sealUsageMapper.selectById(700L)).thenReturn(u);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> sealService.returnUsage(700L));
        assertEquals(409, ex.getCode());
    }

    // 4. destroy 之前有未归还的 usage → 409
    @Test
    void destroy_withActiveUsage_rejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(seal(SealService.STATUS_IN_USE));
        AstSealUsage active = new AstSealUsage();
        active.setId(700L);
        active.setSealId(SEAL_ID);
        active.setReturnedAt(null);
        when(sealUsageMapper.findBySeal(SEAL_ID)).thenReturn(List.of(active));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> sealService.destroy(SEAL_ID, "lost"));
        assertEquals(409, ex.getCode());
    }

    // 5. destroy 全部归还后 → OK
    @Test
    void destroy_allReturned_succeeds() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(seal(SealService.STATUS_IN_USE));
        AstSealUsage returned = new AstSealUsage();
        returned.setId(700L);
        returned.setSealId(SEAL_ID);
        returned.setReturnedAt(LocalDateTime.now());
        when(sealUsageMapper.findBySeal(SEAL_ID)).thenReturn(List.of(returned));
        AstSeal out = sealService.destroy(SEAL_ID, "end of life");
        assertEquals(SealService.STATUS_DESTROYED, out.getStatus());
    }

    // 6. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstSeal s = seal(SealService.STATUS_IN_USE);
        s.setTenantId(99L);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> sealService.getById(SEAL_ID));
        assertEquals(404, ex.getCode());
    }
}