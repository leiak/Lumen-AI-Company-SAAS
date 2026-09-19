package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinPayable;
import com.lumen.finance.mapper.FinPayableMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayableServiceTest {

    @Mock private FinPayableMapper payableMapper;
    @InjectMocks private PayableService payableService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinPayable stub(String status, String amount, String paid) {
        FinPayable p = new FinPayable();
        p.setId(60L);
        p.setTenantId(TID);
        p.setAmount(new BigDecimal(amount));
        p.setPaidAmount(new BigDecimal(paid));
        p.setStatus(status);
        return p;
    }

    @Test
    void pay_partialAmount_updatesToPartial() {
        when(payableMapper.selectById(60L)).thenReturn(
            stub("pending", "1000.00", "0.00"));
        FinPayable r = payableService.pay(60L, new BigDecimal("300"));
        assertEquals(0, r.getPaidAmount().compareTo(new BigDecimal("300")));
        assertEquals("partial", r.getStatus());
    }

    @Test
    void pay_exactAmount_updatesToPaid() {
        when(payableMapper.selectById(60L)).thenReturn(
            stub("partial", "1000.00", "700.00"));
        FinPayable r = payableService.pay(60L, new BigDecimal("300"));
        assertEquals("paid", r.getStatus());
    }

    @Test
    void pay_overAmount_throws400() {
        when(payableMapper.selectById(60L)).thenReturn(
            stub("partial", "1000.00", "900.00"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> payableService.pay(60L, new BigDecimal("200")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void pay_alreadyPaid_throws409() {
        when(payableMapper.selectById(60L)).thenReturn(
            stub("paid", "1000.00", "1000.00"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> payableService.pay(60L, new BigDecimal("1")));
        assertEquals(409, ex.getCode());
    }

    @Test
    void pay_zeroAmount_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> payableService.pay(60L, BigDecimal.ZERO));
        assertEquals(400, ex.getCode());
    }
}
