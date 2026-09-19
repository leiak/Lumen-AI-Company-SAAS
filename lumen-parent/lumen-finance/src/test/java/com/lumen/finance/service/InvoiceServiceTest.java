package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinInvoice;
import com.lumen.finance.mapper.FinInvoiceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private FinInvoiceMapper invoiceMapper;
    @InjectMocks private InvoiceService invoiceService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinInvoice stub(String status) {
        FinInvoice inv = new FinInvoice();
        inv.setId(50L);
        inv.setTenantId(TID);
        inv.setInvoiceNo("INV-001");
        inv.setRecognizeStatus(status);
        return inv;
    }

    // ---------- recognize ----------

    @Test
    void recognize_pending_succeeds() {
        when(invoiceMapper.selectById(50L)).thenReturn(stub("pending"));
        FinInvoice result = invoiceService.recognize(50L);
        assertEquals("recognized", result.getRecognizeStatus());
    }

    @Test
    void recognize_alreadyRecognized_throws409() {
        when(invoiceMapper.selectById(50L)).thenReturn(stub("recognized"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.recognize(50L));
        assertEquals(409, ex.getCode());
    }

    // ---------- certify ----------

    @Test
    void certify_recognized_succeeds() {
        when(invoiceMapper.selectById(50L)).thenReturn(stub("recognized"));
        FinInvoice result = invoiceService.certify(50L);
        assertEquals("certified", result.getRecognizeStatus());
    }

    @Test
    void certify_pending_throws409() {
        when(invoiceMapper.selectById(50L)).thenReturn(stub("pending"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.certify(50L));
        assertEquals(409, ex.getCode());
    }

    // ---------- sensitiveInfo RBAC ----------

    @Test
    void sensitiveInfo_financeAdmin_returnsPlaintext() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).roles(Set.of("finance_admin")).build());
        when(invoiceMapper.selectById(50L)).thenReturn(stub("pending"));
        java.util.Map<String, String> result = invoiceService.sensitiveInfo(50L);
        assertNotNull(result);
        assertTrue(result.containsKey("buyerName"));
        assertTrue(result.containsKey("sellerName"));
        assertTrue(result.containsKey("taxNo"));
    }

    @Test
    void sensitiveInfo_superAdmin_returnsPlaintext() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).roles(Set.of("super_admin")).build());
        when(invoiceMapper.selectById(50L)).thenReturn(stub("pending"));
        java.util.Map<String, String> result = invoiceService.sensitiveInfo(50L);
        assertNotNull(result);
    }

    @Test
    void sensitiveInfo_regularUser_throws403() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).roles(Set.of("user")).build());
        // mapper stub intentionally absent: 403 fires before DB lookup.
        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.sensitiveInfo(50L));
        assertEquals(403, ex.getCode());
    }

    // ---------- get cross-tenant ----------

    @Test
    void get_crossTenant_returns404() {
        FinInvoice inv = stub("pending");
        inv.setTenantId(999L);
        when(invoiceMapper.selectById(50L)).thenReturn(inv);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> invoiceService.get(50L));
        assertEquals(404, ex.getCode());
    }
}
