package com.lumen.procurement.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.entity.ProcSupplierQualification;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import com.lumen.procurement.mapper.ProcSupplierQualificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcSupplierQualificationServiceTest {

    @Mock private ProcSupplierQualificationMapper qualificationMapper;
    @Mock private ProcSupplierMapper supplierMapper;
    @InjectMocks private ProcSupplierQualificationService qualificationService;

    private static final long TENANT = 1L;
    private static final long SUPPLIER_ID = 100L;
    private static final long QUAL_ID = 200L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(10L).tenantId(TENANT).userName("alice")
            .roles(Set.of("procurement_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private ProcSupplierQualification stub(String status, LocalDate expireAt, long tenant) {
        ProcSupplierQualification q = new ProcSupplierQualification();
        q.setId(QUAL_ID);
        q.setSupplierId(SUPPLIER_ID);
        q.setQualificationType("business_license");
        q.setStatus(status);
        q.setExpireAt(expireAt);
        q.setTenantId(tenant);
        return q;
    }

    // 10. approve 仅 pending 可批
    @Test
    void approve_alreadyApproved_throws409() {
        ProcSupplierQualification q = stub("approved", LocalDate.now().plusDays(60), TENANT);
        when(qualificationMapper.selectById(QUAL_ID)).thenReturn(q);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> qualificationService.approve(QUAL_ID));
        assertEquals(409, ex.getCode());
    }

    // 11. approve 成功
    @Test
    void approve_pending_setsApproved() {
        ProcSupplierQualification q = stub("pending", LocalDate.now().plusDays(60), TENANT);
        when(qualificationMapper.selectById(QUAL_ID)).thenReturn(q);
        ProcSupplierQualification result = qualificationService.approve(QUAL_ID);
        assertEquals("approved", result.getStatus());
    }

    // 12. expired 自动标记 (lazy)
    @Test
    void getById_expiredApproved_lazilyMarksExpired() {
        ProcSupplierQualification q = stub("approved", LocalDate.now().minusDays(1), TENANT);
        when(qualificationMapper.selectById(QUAL_ID)).thenReturn(q);
        ProcSupplierQualification got = qualificationService.getById(QUAL_ID);
        assertEquals("expired", got.getStatus());
    }

    // 13. findExpiring days 校验
    @Test
    void findExpiring_invalidRange_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> qualificationService.findExpiring(0));
        assertEquals(400, ex.getCode());
        ServiceException ex2 = assertThrows(ServiceException.class,
            () -> qualificationService.findExpiring(400));
        assertEquals(400, ex2.getCode());
    }

    // 14. create 同租户校验
    @Test
    void create_crossTenantSupplier_throws400() {
        ProcSupplier s = new ProcSupplier();
        s.setId(SUPPLIER_ID);
        s.setTenantId(99L);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(s);

        ProcSupplierQualification req = new ProcSupplierQualification();
        req.setSupplierId(SUPPLIER_ID);
        req.setQualificationType("business_license");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> qualificationService.create(req));
        assertEquals(400, ex.getCode());
    }
}