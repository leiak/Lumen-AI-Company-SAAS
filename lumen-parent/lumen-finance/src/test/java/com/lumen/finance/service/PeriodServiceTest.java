package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinPayable;
import com.lumen.finance.entity.FinPeriod;
import com.lumen.finance.entity.FinReceivable;
import com.lumen.finance.entity.FinVoucher;
import com.lumen.finance.mapper.FinPayableMapper;
import com.lumen.finance.mapper.FinPeriodMapper;
import com.lumen.finance.mapper.FinReceivableMapper;
import com.lumen.finance.mapper.FinVoucherMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeriodServiceTest {

    @Mock private FinPeriodMapper periodMapper;
    @Mock private FinVoucherMapper voucherMapper;
    @Mock private FinReceivableMapper receivableMapper;
    @Mock private FinPayableMapper payableMapper;

    @InjectMocks private PeriodService periodService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinPeriod stubPeriod(String status) {
        FinPeriod p = new FinPeriod();
        p.setId(1L);
        p.setTenantId(TID);
        p.setYear(2026);
        p.setMonth(1);
        p.setStatus(status);
        return p;
    }

    // ---------- close ----------

    @Test
    void close_openPeriod_allChecksPass_closes() {
        FinPeriod p = stubPeriod("open");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        when(voucherMapper.findByPeriod("2026-01")).thenReturn(List.of());
        when(receivableMapper.listOpenForCloseCheck()).thenReturn(List.of());
        when(payableMapper.listOpenForCloseCheck()).thenReturn(List.of());

        FinPeriod result = periodService.close("2026-01");

        assertEquals("closed", result.getStatus());
        assertNotNull(result.getClosedAt());
        ArgumentCaptor<FinPeriod> cap = ArgumentCaptor.forClass(FinPeriod.class);
        verify(periodMapper).updateById(cap.capture());
        assertEquals("closed", cap.getValue().getStatus());
    }

    @Test
    void close_draftVoucher_throws409() {
        FinPeriod p = stubPeriod("open");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        FinVoucher v = new FinVoucher();
        v.setId(7L);
        v.setStatus("draft");
        when(voucherMapper.findByPeriod("2026-01")).thenReturn(List.of(v));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("draft"));
    }

    @Test
    void close_postedVouchersButOpenReceivable_throws409() {
        FinPeriod p = stubPeriod("open");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        FinVoucher v = new FinVoucher();
        v.setId(7L);
        v.setStatus("posted");
        when(voucherMapper.findByPeriod("2026-01")).thenReturn(List.of(v));
        FinReceivable r = new FinReceivable();
        r.setId(99L);
        r.setTenantId(TID);
        r.setStatus("pending");
        when(receivableMapper.listOpenForCloseCheck()).thenReturn(List.of(r));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("receivable"));
    }

    @Test
    void close_openPayable_throws409() {
        FinPeriod p = stubPeriod("open");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        when(voucherMapper.findByPeriod("2026-01")).thenReturn(List.of());
        when(receivableMapper.listOpenForCloseCheck()).thenReturn(List.of());
        FinPayable pay = new FinPayable();
        pay.setId(8L);
        pay.setTenantId(TID);
        pay.setStatus("partial");
        when(payableMapper.listOpenForCloseCheck()).thenReturn(List.of(pay));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("payable"));
    }

    @Test
    void close_alreadyClosed_throws409() {
        FinPeriod p = stubPeriod("closed");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void close_lockedPeriod_throws403() {
        FinPeriod p = stubPeriod("locked");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void close_otherTenantPeriod_throws404() {
        FinPeriod p = stubPeriod("open");
        p.setTenantId(999L);
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("2026-01"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void close_invalidPeriod_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.close("bad"));
        assertEquals(400, ex.getCode());
    }

    // ---------- lock ----------

    @Test
    void lock_closedPeriod_succeeds() {
        FinPeriod p = stubPeriod("closed");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        FinPeriod result = periodService.lock("2026-01");
        assertEquals("locked", result.getStatus());
    }

    @Test
    void lock_openPeriod_throws409() {
        FinPeriod p = stubPeriod("open");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.lock("2026-01"));
        assertEquals(409, ex.getCode());
    }

    // ---------- nextPeriod ----------

    @Test
    void nextPeriod_decemberToJanuaryJanuary() {
        assertEquals("2027-01", periodService.nextPeriod("2026-12"));
    }

    @Test
    void nextPeriod_normalMonth() {
        assertEquals("2026-03", periodService.nextPeriod("2026-02"));
    }

    @Test
    void nextPeriod_invalidFormat_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> periodService.nextPeriod("2026/01"));
        assertEquals(400, ex.getCode());
    }
}
