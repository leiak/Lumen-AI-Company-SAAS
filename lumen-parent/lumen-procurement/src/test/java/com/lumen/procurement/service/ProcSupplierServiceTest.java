package com.lumen.procurement.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.SaveSupplierRequest;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import com.lumen.procurement.mapper.ProcSupplierQualificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcSupplierServiceTest {

    @Mock private ProcSupplierMapper supplierMapper;
    @Mock private ProcSupplierQualificationMapper qualificationMapper;
    @InjectMocks private ProcSupplierService supplierService;

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long SUPPLIER_ID = 100L;
    private static final long USER = 10L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(Set.of("procurement_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private ProcSupplier stub(String status, long tenant) {
        ProcSupplier s = new ProcSupplier();
        s.setId(SUPPLIER_ID);
        s.setCode("SUP-001");
        s.setName("Acme");
        s.setStatus(status);
        s.setLevel(1);
        s.setRating(BigDecimal.ZERO);
        s.setTenantId(tenant);
        return s;
    }

    // 1. 跨租户 → 404
    @Test
    void getById_crossTenant_returns404() {
        ProcSupplier s = stub("active", OTHER_TENANT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.getById(SUPPLIER_ID));
        assertEquals(404, ex.getCode());
    }

    // 2. 缺失 context → 401
    @Test
    void getById_missingContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.getById(SUPPLIER_ID));
        assertEquals(401, ex.getCode());
    }

    // 3. rating 超出 5
    @Test
    void create_ratingAbove5_throws400() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setCode("SUP-100");
        req.setName("X");
        req.setTaxNo("T");
        req.setRating(new BigDecimal("5.50"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 4. rating 负数
    @Test
    void create_ratingNegative_throws400() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setCode("SUP-100");
        req.setName("X");
        req.setTaxNo("T");
        req.setRating(new BigDecimal("-0.01"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 5. level 越界
    @Test
    void create_levelOutOfRange_throws400() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setCode("SUP-100");
        req.setName("X");
        req.setTaxNo("T");
        req.setLevel(6);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.create(req));
        assertEquals(400, ex.getCode());
    }

    // 6. 创建成功
    @Test
    void create_validRequest_persists() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setCode("SUP-100");
        req.setName("Acme");
        req.setTaxNo("TAX-1");
        req.setLevel(3);
        req.setRating(new BigDecimal("4.50"));
        when(supplierMapper.findByCode(eq(TENANT), eq("SUP-100"))).thenReturn(null);
        when(supplierMapper.insert(any(ProcSupplier.class))).thenAnswer(inv -> {
            ProcSupplier arg = inv.getArgument(0);
            arg.setId(SUPPLIER_ID);
            return 1;
        });
        ProcSupplier created = supplierService.create(req);
        assertEquals(SUPPLIER_ID, created.getId());
        assertEquals("pending", created.getStatus());
        assertEquals(TENANT, created.getTenantId());
    }

    // 7. 重复 code
    @Test
    void create_duplicateCode_throws409() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setCode("SUP-DUP");
        req.setName("X");
        req.setTaxNo("T");
        when(supplierMapper.findByCode(eq(TENANT), eq("SUP-DUP"))).thenReturn(stub("active", TENANT));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.create(req));
        assertEquals(409, ex.getCode());
    }

    // 8. 黑名单幂等保护
    @Test
    void blacklist_alreadyBlacklisted_throws409() {
        ProcSupplier s = stub(ProcSupplierService.STATUS_BLACKLIST, TENANT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> supplierService.blacklist(SUPPLIER_ID, "dup"));
        assertEquals(409, ex.getCode());
    }

    // 9. 黑名单 → 解除 → active
    @Test
    void blacklist_thenUnblacklist_setsActive() {
        ProcSupplier s = stub(ProcSupplierService.STATUS_ACTIVE, TENANT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(s);
        ProcSupplier blacklisted = supplierService.blacklist(SUPPLIER_ID, "test");
        assertEquals(ProcSupplierService.STATUS_BLACKLIST, blacklisted.getStatus());
        ProcSupplier active = supplierService.unblacklist(SUPPLIER_ID);
        assertEquals(ProcSupplierService.STATUS_ACTIVE, active.getStatus());
    }
}