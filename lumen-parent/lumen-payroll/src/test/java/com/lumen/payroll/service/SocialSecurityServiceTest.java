package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.CalculateSocialSecurityRequest;
import com.lumen.payroll.entity.PaySocialSecurity;
import com.lumen.payroll.mapper.PaySocialSecurityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialSecurityServiceTest {

    @Mock private PaySocialSecurityMapper mapper;

    @InjectMocks private SocialSecurityService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    // ---------- calculate: 个人 + 公司部分 ----------

    @Test
    void calculate_withinRange_persistsAmounts() {
        when(mapper.findByEmployeeAndPeriod(any(), any())).thenReturn(null);
        when(mapper.insert(any(PaySocialSecurity.class))).thenAnswer(inv -> {
            PaySocialSecurity s = inv.getArgument(0);
            s.setId(50L);
            return 1;
        });
        CalculateSocialSecurityRequest req = new CalculateSocialSecurityRequest();
        req.setEmployeeId(200L);
        req.setPeriod("2026-09");
        req.setBaseAmount(new BigDecimal("10000.00"));

        PaySocialSecurity s = service.calculate(req);

        assertEquals(50L, s.getId());
        assertEquals("calculated", s.getStatus());

        // 个人比例总和: pension 0.08 + medical 0.02 + unemployment 0.005 + housingFund 0.07 = 0.175
        // 10000 * 0.175 = 1750.00
        assertEquals(0, s.getEmployeeAmount().compareTo(new BigDecimal("1750.00")));

        // 公司比例总和: pension 0.16 + medical 0.10 + unemployment 0.005 + injury 0.01 + maternity 0.008 + housingFund 0.07 = 0.353
        // 10000 * 0.353 = 3530.00
        assertEquals(0, s.getEmployerAmount().compareTo(new BigDecimal("3530.00")));

        // items 必须含 6 项
        Map<String, Object> items = s.getItems();
        assertEquals(6, items.size());
        assertTrue(items.containsKey("pension"));
        assertTrue(items.containsKey("medical"));
        assertTrue(items.containsKey("unemployment"));
        assertTrue(items.containsKey("injury"));
        assertTrue(items.containsKey("maternity"));
        assertTrue(items.containsKey("housingFund"));
    }

    @Test
    void calculate_baseBelowMin_throws400() {
        CalculateSocialSecurityRequest req = new CalculateSocialSecurityRequest();
        req.setEmployeeId(200L);
        req.setPeriod("2026-09");
        req.setBaseAmount(new BigDecimal("1000.00")); // < 3613

        ServiceException ex = assertThrows(ServiceException.class, () -> service.calculate(req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("out of range"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void calculate_baseAboveMax_throws400() {
        CalculateSocialSecurityRequest req = new CalculateSocialSecurityRequest();
        req.setEmployeeId(200L);
        req.setPeriod("2026-09");
        req.setBaseAmount(new BigDecimal("50000.00")); // > 31884

        ServiceException ex = assertThrows(ServiceException.class, () -> service.calculate(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void calculate_atMinBoundary_succeeds() {
        when(mapper.findByEmployeeAndPeriod(any(), any())).thenReturn(null);
        when(mapper.insert(any(PaySocialSecurity.class))).thenAnswer(inv -> {
            PaySocialSecurity s = inv.getArgument(0);
            s.setId(60L);
            return 1;
        });
        CalculateSocialSecurityRequest req = new CalculateSocialSecurityRequest();
        req.setEmployeeId(200L);
        req.setPeriod("2026-09");
        req.setBaseAmount(new BigDecimal("3613.00"));
        PaySocialSecurity s = service.calculate(req);
        // 个人: 3613 * 0.175 = 632.275 → 632.28
        assertEquals(0, s.getEmployeeAmount().compareTo(new BigDecimal("632.28")));
    }

    @Test
    void calculate_existingRecord_updates() {
        PaySocialSecurity existing = new PaySocialSecurity();
        existing.setId(70L);
        existing.setTenantId(TID);
        existing.setEmployeeId(200L);
        existing.setPeriod("2026-09");
        when(mapper.findByEmployeeAndPeriod(200L, "2026-09")).thenReturn(existing);
        CalculateSocialSecurityRequest req = new CalculateSocialSecurityRequest();
        req.setEmployeeId(200L);
        req.setPeriod("2026-09");
        req.setBaseAmount(new BigDecimal("10000.00"));
        service.calculate(req);
        verify(mapper, never()).insert(any());
        verify(mapper).updateById(existing);
    }

    // ---------- declare: 批量 calculated → declared ----------

    @Test
    void declare_skipsNonCalculatedAndCounts() {
        PaySocialSecurity c1 = new PaySocialSecurity();
        c1.setStatus("calculated");
        PaySocialSecurity c2 = new PaySocialSecurity();
        c2.setStatus("calculated");
        PaySocialSecurity p = new PaySocialSecurity();
        p.setStatus("declared"); // 已经是 declared
        when(mapper.findByPeriod("2026-09")).thenReturn(java.util.List.of(c1, c2, p));
        int count = service.declare("2026-09");
        assertEquals(2, count);
        ArgumentCaptor<PaySocialSecurity> cap = ArgumentCaptor.forClass(PaySocialSecurity.class);
        verify(mapper, times(2)).updateById(cap.capture());
        cap.getAllValues().forEach(s -> assertEquals("declared", s.getStatus()));
    }

    @Test
    void declare_invalidPeriod_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.declare("2026-13"));
        assertEquals(400, ex.getCode());
    }
}