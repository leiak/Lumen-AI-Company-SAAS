package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.dto.SaveVoucherRequest;
import com.lumen.finance.dto.VoucherEntryDto;
import com.lumen.finance.entity.FinPeriod;
import com.lumen.finance.entity.FinVoucher;
import com.lumen.finance.entity.FinVoucherEntry;
import com.lumen.finance.mapper.FinPeriodMapper;
import com.lumen.finance.mapper.FinVoucherEntryMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock private FinVoucherMapper voucherMapper;
    @Mock private FinVoucherEntryMapper entryMapper;
    @Mock private FinPeriodMapper periodMapper;

    @InjectMocks private VoucherService voucherService;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    // ---------- helpers ----------
    private SaveVoucherRequest balancedRequest() {
        SaveVoucherRequest req = new SaveVoucherRequest();
        req.setPeriod("2026-01");
        req.setVoucherDate(LocalDate.of(2026, 1, 15));
        req.setSummary("test");
        VoucherEntryDto d = new VoucherEntryDto();
        d.setSubjectId(11L);
        d.setDebitAmount(new BigDecimal("100.00"));
        VoucherEntryDto c = new VoucherEntryDto();
        c.setSubjectId(12L);
        c.setCreditAmount(new BigDecimal("100.00"));
        req.setEntries(List.of(d, c));
        return req;
    }

    private FinPeriod stubOpenPeriod(String period) {
        FinPeriod p = new FinPeriod();
        p.setId(1L);
        p.setTenantId(TID);
        p.setYear(Integer.parseInt(period.substring(0, 4)));
        p.setMonth(Integer.parseInt(period.substring(5, 7)));
        p.setStatus("open");
        return p;
    }

    private FinVoucher stubVoucher(long id, String status) {
        FinVoucher v = new FinVoucher();
        v.setId(id);
        v.setTenantId(TID);
        v.setPeriod("2026-01");
        v.setTotalDebit(new BigDecimal("100.00"));
        v.setTotalCredit(new BigDecimal("100.00"));
        v.setStatus(status);
        return v;
    }

    // ---------- save: balance + xor ----------

    @Test
    void save_balancedEntries_persistsVoucherAndEntries() {
        when(voucherMapper.insert(any(FinVoucher.class))).thenAnswer(inv -> {
            FinVoucher v = inv.getArgument(0);
            v.setId(42L);
            return 1;
        });
        when(entryMapper.insert(any(FinVoucherEntry.class))).thenReturn(1);

        FinVoucher v = voucherService.save(balancedRequest());

        assertNotNull(v.getId());
        assertEquals("draft", v.getStatus());
        assertEquals(new BigDecimal("100.00"), v.getTotalDebit());
        assertEquals(new BigDecimal("100.00"), v.getTotalCredit());
        ArgumentCaptor<FinVoucherEntry> cap = ArgumentCaptor.forClass(FinVoucherEntry.class);
        verify(entryMapper, times(2)).insert(cap.capture());
    }

    @Test
    void save_unbalancedEntries_throws400() {
        SaveVoucherRequest req = balancedRequest();
        VoucherEntryDto d = req.getEntries().get(0);
        d.setDebitAmount(new BigDecimal("150.00")); // debit != credit

        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.save(req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("balanced"));
        verify(voucherMapper, never()).insert(any(FinVoucher.class));
    }

    @Test
    void save_entryBothDebitAndCredit_throws400() {
        SaveVoucherRequest req = balancedRequest();
        VoucherEntryDto d = req.getEntries().get(0);
        d.setDebitAmount(new BigDecimal("100"));
        d.setCreditAmount(new BigDecimal("100")); // both > 0 → invalid

        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.save(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_noTenant_throws401() {
        UserContextHolder.clear();
        UserContextHolder.set(UserContext.builder().userId(UID).build()); // no tenant
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.save(balancedRequest()));
        assertEquals(401, ex.getCode());
    }

    // ---------- post: 借贷必平 + 期间 open ----------

    @Test
    void post_balancedAndOpenPeriod_succeeds() {
        FinVoucher v = stubVoucher(7L, "draft");
        when(voucherMapper.selectById(7L)).thenReturn(v);
        FinVoucherEntry e1 = new FinVoucherEntry();
        e1.setDebitAmount(new BigDecimal("100.00"));
        e1.setCreditAmount(BigDecimal.ZERO);
        FinVoucherEntry e2 = new FinVoucherEntry();
        e2.setDebitAmount(BigDecimal.ZERO);
        e2.setCreditAmount(new BigDecimal("100.00"));
        when(entryMapper.findByVoucher(7L)).thenReturn(List.of(e1, e2));
        FinPeriod period = stubOpenPeriod("2026-01");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);

        FinVoucher result = voucherService.post(7L);

        assertEquals("posted", result.getStatus());
        assertNotNull(result.getPostedAt());
        assertEquals(UID, result.getPostedBy());
    }

    @Test
    void post_unbalancedEntries_throws400() {
        FinVoucher v = stubVoucher(8L, "draft");
        when(voucherMapper.selectById(8L)).thenReturn(v);
        FinVoucherEntry e1 = new FinVoucherEntry();
        e1.setDebitAmount(new BigDecimal("100.00"));
        e1.setCreditAmount(BigDecimal.ZERO);
        FinVoucherEntry e2 = new FinVoucherEntry();
        e2.setDebitAmount(BigDecimal.ZERO);
        e2.setCreditAmount(new BigDecimal("50.00"));
        when(entryMapper.findByVoucher(8L)).thenReturn(List.of(e1, e2));
        // period stub is intentionally absent: unbalanced entries fail before period lookup.

        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.post(8L));
        assertEquals(400, ex.getCode());
        verify(voucherMapper, never()).updateById(any());
    }

    @Test
    void post_alreadyPosted_throws409() {
        FinVoucher v = stubVoucher(9L, "posted");
        when(voucherMapper.selectById(9L)).thenReturn(v);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.post(9L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void post_periodClosed_throws409() {
        FinVoucher v = stubVoucher(10L, "draft");
        when(voucherMapper.selectById(10L)).thenReturn(v);
        FinVoucherEntry e1 = new FinVoucherEntry();
        e1.setDebitAmount(new BigDecimal("100"));
        e1.setCreditAmount(BigDecimal.ZERO);
        FinVoucherEntry e2 = new FinVoucherEntry();
        e2.setDebitAmount(BigDecimal.ZERO);
        e2.setCreditAmount(new BigDecimal("100"));
        when(entryMapper.findByVoucher(10L)).thenReturn(List.of(e1, e2));
        FinPeriod period = stubOpenPeriod("2026-01");
        period.setStatus("closed");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.post(10L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("not open"));
    }

    @Test
    void post_periodLocked_throws409() {
        FinVoucher v = stubVoucher(11L, "draft");
        when(voucherMapper.selectById(11L)).thenReturn(v);
        FinVoucherEntry e1 = new FinVoucherEntry();
        e1.setDebitAmount(new BigDecimal("100"));
        e1.setCreditAmount(BigDecimal.ZERO);
        FinVoucherEntry e2 = new FinVoucherEntry();
        e2.setDebitAmount(BigDecimal.ZERO);
        e2.setCreditAmount(new BigDecimal("100"));
        when(entryMapper.findByVoucher(11L)).thenReturn(List.of(e1, e2));
        FinPeriod period = stubOpenPeriod("2026-01");
        period.setStatus("locked");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.post(11L));
        assertEquals(409, ex.getCode());
    }

    // ---------- reverse ----------

    @Test
    void reverse_postedVoucher_createsReversalAndMarksOriginal() {
        FinVoucher orig = stubVoucher(20L, "posted");
        orig.setVoucherNo("V-orig");
        orig.setVoucherDate(LocalDate.of(2026, 1, 15));
        when(voucherMapper.selectById(20L)).thenReturn(orig);
        FinPeriod period = stubOpenPeriod("2026-01");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);
        when(voucherMapper.insert(any(FinVoucher.class))).thenAnswer(inv -> {
            FinVoucher v = inv.getArgument(0);
            v.setId(30L);
            return 1;
        });
        FinVoucherEntry e1 = new FinVoucherEntry();
        e1.setSubjectId(11L);
        e1.setDebitAmount(new BigDecimal("100"));
        e1.setCreditAmount(BigDecimal.ZERO);
        FinVoucherEntry e2 = new FinVoucherEntry();
        e2.setSubjectId(12L);
        e2.setDebitAmount(BigDecimal.ZERO);
        e2.setCreditAmount(new BigDecimal("100"));
        when(entryMapper.findByVoucher(20L)).thenReturn(List.of(e1, e2));
        when(entryMapper.insert(any(FinVoucherEntry.class))).thenReturn(1);

        FinVoucher rev = voucherService.reverse(20L, "error correction");

        assertNotNull(rev.getId());
        assertEquals(orig.getId(), rev.getReversedId());
        assertEquals("posted", rev.getStatus());
        // Reversal entries swap debit/credit
        ArgumentCaptor<FinVoucherEntry> cap = ArgumentCaptor.forClass(FinVoucherEntry.class);
        verify(entryMapper, times(2)).insert(cap.capture());
        List<FinVoucherEntry> inserted = cap.getAllValues();
        // Both lines must have one of (debit, credit) as 0 and the other == 100
        for (FinVoucherEntry r : inserted) {
            BigDecimal d = r.getDebitAmount() == null ? BigDecimal.ZERO : r.getDebitAmount();
            BigDecimal c = r.getCreditAmount() == null ? BigDecimal.ZERO : r.getCreditAmount();
            assertTrue(d.signum() == 0 ^ c.signum() == 0,
                "reversal entry must have exactly one side > 0");
        }
        // Original should be flipped to reversed
        ArgumentCaptor<FinVoucher> origCap = ArgumentCaptor.forClass(FinVoucher.class);
        verify(voucherMapper, atLeastOnce()).updateById(origCap.capture());
        FinVoucher updatedOrig = origCap.getAllValues().stream()
            .filter(o -> o.getId() != null && o.getId().equals(20L))
            .findFirst().orElseThrow();
        assertEquals("reversed", updatedOrig.getStatus());
        assertEquals(30L, updatedOrig.getReversedId());
    }

    @Test
    void reverse_draftVoucher_throws409() {
        FinVoucher v = stubVoucher(21L, "draft");
        when(voucherMapper.selectById(21L)).thenReturn(v);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.reverse(21L, "x"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void reverse_lockedPeriod_throws403() {
        FinVoucher v = stubVoucher(22L, "posted");
        when(voucherMapper.selectById(22L)).thenReturn(v);
        FinPeriod period = stubOpenPeriod("2026-01");
        period.setStatus("locked");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.reverse(22L, "x"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void reverse_missingReason_throws400() {
        FinVoucher v = stubVoucher(23L, "posted");
        when(voucherMapper.selectById(23L)).thenReturn(v);
        FinPeriod period = stubOpenPeriod("2026-01");
        when(periodMapper.findByYearMonth(2026, 1)).thenReturn(period);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.reverse(23L, ""));
        assertEquals(400, ex.getCode());
    }

    // ---------- get: cross-tenant 404 ----------

    @Test
    void get_crossTenant_returns404NotForbidden() {
        FinVoucher v = new FinVoucher();
        v.setId(50L);
        v.setTenantId(999L); // different tenant
        when(voucherMapper.selectById(50L)).thenReturn(v);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> voucherService.get(50L));
        assertEquals(404, ex.getCode());
    }
}
