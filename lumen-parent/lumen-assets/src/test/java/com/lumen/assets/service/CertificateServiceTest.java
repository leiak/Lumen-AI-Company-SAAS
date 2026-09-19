package com.lumen.assets.service;

import com.lumen.assets.entity.AstCertificate;
import com.lumen.assets.mapper.AstCertificateMapper;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Certificate: deriveStatus — 30/15/7 三档（默认 30）。
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private AstCertificateMapper certificateMapper;
    @InjectMocks private CertificateService certificateService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long CERT_ID = 1000L;

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

    private AstCertificate cert(LocalDate expire) {
        AstCertificate c = new AstCertificate();
        c.setId(CERT_ID);
        c.setTenantId(TENANT);
        c.setCertificateType(CertificateService.TYPE_BUSINESS_LICENSE);
        c.setCertificateNo("B-001");
        c.setHolder("Lumen Inc");
        c.setIssueDate(LocalDate.of(2020, 1, 1));
        c.setExpireAt(expire);
        return c;
    }

    // 1. deriveStatus 已过期
    @Test
    void deriveStatus_expired() {
        LocalDate past = LocalDate.now().minusDays(10);
        assertEquals("expired", CertificateService.deriveStatus(past));
    }

    // 2. deriveStatus ≤ 7 → critical
    @Test
    void deriveStatus_critical() {
        LocalDate within7 = LocalDate.now().plusDays(5);
        assertEquals("critical", CertificateService.deriveStatus(within7));
    }

    // 3. deriveStatus ≤ 15 → warning
    @Test
    void deriveStatus_warning() {
        LocalDate within15 = LocalDate.now().plusDays(10);
        assertEquals("warning", CertificateService.deriveStatus(within15));
    }

    // 4. deriveStatus ≤ 30 → normal
    @Test
    void deriveStatus_normal() {
        LocalDate within30 = LocalDate.now().plusDays(20);
        assertEquals("normal", CertificateService.deriveStatus(within30));
    }

    // 5. deriveStatus 超过 30 天 → active
    @Test
    void deriveStatus_active() {
        LocalDate future = LocalDate.now().plusYears(1);
        assertEquals("active", CertificateService.deriveStatus(future));
    }

    // 6. expiringWithin 默认 30 天窗口 — 包含 within30、within15、within7、已过期都被纳入
    @Test
    void expiringWithin_default30_includesAllTiers() {
        AstCertificate c1 = cert(LocalDate.now().plusDays(20)); // normal
        AstCertificate c2 = cert(LocalDate.now().plusDays(10)); // warning
        AstCertificate c3 = cert(LocalDate.now().plusDays(3));  // critical
        AstCertificate c4 = cert(LocalDate.now().minusDays(5)); // expired
        when(certificateMapper.findExpiringWithin(any())).thenReturn(new ArrayList<>(List.of(c1, c2, c3, c4)));
        List<AstCertificate> out = certificateService.expiringWithin(null);
        assertEquals(4, out.size());
        assertEquals("normal", out.get(0).getStatus());
        assertEquals("warning", out.get(1).getStatus());
        assertEquals("critical", out.get(2).getStatus());
        assertEquals("expired", out.get(3).getStatus());
    }

    // 7. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstCertificate c = cert(LocalDate.now().plusDays(20));
        c.setTenantId(99L);
        when(certificateMapper.selectById(CERT_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> certificateService.getById(CERT_ID));
        assertEquals(404, ex.getCode());
    }
}