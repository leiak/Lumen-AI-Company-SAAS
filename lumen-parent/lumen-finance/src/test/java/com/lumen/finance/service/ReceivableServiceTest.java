package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinReceivable;
import com.lumen.finance.mapper.FinReceivableMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    @Mock private FinReceivableMapper receivableMapper;
    @InjectMocks private ReceivableService receivableService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinReceivable stubReceivable(String status, String amount, String collected) {
        FinReceivable r = new FinReceivable();
        r.setId(50L);
        r.setTenantId(TID);
        r.setAmount(new BigDecimal(amount));
        r.setCollectedAmount(new BigDecimal(collected));
        r.setStatus(status);
        return r;
    }

    @Test
    void collect_partialAmount_updatesToPartial() {
        when(receivableMapper.selectById(50L)).thenReturn(
            stubReceivable("pending", "1000.00", "0.00"));
        FinReceivable result = receivableService.collect(50L, new BigDecimal("300"));
        assertEquals(0, result.getCollectedAmount().compareTo(new BigDecimal("300")));
        assertEquals("partial", result.getStatus());
    }

    @Test
    void collect_exactAmount_updatesToCollected() {
        when(receivableMapper.selectById(50L)).thenReturn(
            stubReceivable("partial", "1000.00", "700.00"));
        FinReceivable result = receivableService.collect(50L, new BigDecimal("300"));
        assertEquals(0, result.getCollectedAmount().compareTo(new BigDecimal("1000")));
        assertEquals("collected", result.getStatus());
    }

    @Test
    void collect_overAmount_throws400() {
        when(receivableMapper.selectById(50L)).thenReturn(
            stubReceivable("partial", "1000.00", "900.00"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.collect(50L, new BigDecimal("200")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void collect_alreadyCollected_throws409() {
        when(receivableMapper.selectById(50L)).thenReturn(
            stubReceivable("collected", "1000.00", "1000.00"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.collect(50L, new BigDecimal("1")));
        assertEquals(409, ex.getCode());
    }

    @Test
    void collect_zeroAmount_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.collect(50L, BigDecimal.ZERO));
        assertEquals(400, ex.getCode());
    }

    @Test
    void collect_crossTenant_throws404() {
        FinReceivable r = stubReceivable("pending", "1000.00", "0.00");
        r.setTenantId(999L);
        when(receivableMapper.selectById(50L)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.collect(50L, new BigDecimal("100")));
        assertEquals(404, ex.getCode());
    }
}
