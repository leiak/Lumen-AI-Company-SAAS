package com.lumen.payroll.service;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.entity.PayTax;
import com.lumen.payroll.mapper.PayTaxMapper;
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
class TaxServiceTest {

    @Mock private PayTaxMapper taxMapper;

    @InjectMocks private TaxService taxService;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    // ---------- 7 级累进表: 各级税额手动算 + 公式校准 ----------

    @Test
    void sevenBracket_table_returnsExpectedRateForEachLevel() {
        // Level 1: ≤ 36000 → 3%, quick=0
        assertEquals(0, taxService.taxAtBracket(1, new BigDecimal("10000")).compareTo(new BigDecimal("300.00")));
        assertEquals(0, taxService.taxAtBracket(1, new BigDecimal("36000")).compareTo(new BigDecimal("1080.00")));

        // Level 2: 36000-144000 → 10%, quick=2520
        // 50000 * 0.10 - 2520 = 2480
        assertEquals(0, taxService.taxAtBracket(2, new BigDecimal("50000")).compareTo(new BigDecimal("2480.00")));

        // Level 3: 144000-300000 → 20%, quick=16920
        // 200000 * 0.20 - 16920 = 23080
        assertEquals(0, taxService.taxAtBracket(3, new BigDecimal("200000")).compareTo(new BigDecimal("23080.00")));

        // Level 4: 300000-420000 → 25%, quick=31920
        // 400000 * 0.25 - 31920 = 68080
        assertEquals(0, taxService.taxAtBracket(4, new BigDecimal("400000")).compareTo(new BigDecimal("68080.00")));

        // Level 5: 420000-660000 → 30%, quick=52920
        // 500000 * 0.30 - 52920 = 97080
        assertEquals(0, taxService.taxAtBracket(5, new BigDecimal("500000")).compareTo(new BigDecimal("97080.00")));

        // Level 6: 660000-960000 → 35%, quick=85920
        // 800000 * 0.35 - 85920 = 194080
        assertEquals(0, taxService.taxAtBracket(6, new BigDecimal("800000")).compareTo(new BigDecimal("194080.00")));

        // Level 7: > 960000 → 45%, quick=181920
        // 1000000 * 0.45 - 181920 = 268080
        assertEquals(0, taxService.taxAtBracket(7, new BigDecimal("1000000")).compareTo(new BigDecimal("268080.00")));
    }

    @Test
    void bracketIndex_picksCorrectLevel() {
        assertEquals(0, taxService.bracketIndex(new BigDecimal("10000")));
        assertEquals(0, taxService.bracketIndex(new BigDecimal("36000")));
        assertEquals(1, taxService.bracketIndex(new BigDecimal("36001")));
        assertEquals(2, taxService.bracketIndex(new BigDecimal("200000")));
        assertEquals(3, taxService.bracketIndex(new BigDecimal("400000")));
        assertEquals(4, taxService.bracketIndex(new BigDecimal("500000")));
        assertEquals(5, taxService.bracketIndex(new BigDecimal("800000")));
        assertEquals(6, taxService.bracketIndex(new BigDecimal("2000000")));
    }

    // ---------- calculateTax: 用 taxableIncome (月度) ----------

    @Test
    void calculateTax_negativeTaxableIncome_returnsZero() {
        BigDecimal tax = taxService.calculateTax(new BigDecimal("-100"), BigDecimal.ZERO, null);
        assertEquals(0, tax.compareTo(BigDecimal.ZERO.setScale(2)));
    }

    @Test
    void calculateTax_zeroTaxableIncome_returnsZero() {
        BigDecimal tax = taxService.calculateTax(BigDecimal.ZERO, BigDecimal.ZERO, null);
        assertEquals(0, tax.compareTo(BigDecimal.ZERO.setScale(2)));
    }

    @Test
    void calculateTax_positiveTaxableIncome_appliesBracket() {
        // taxableIncome = 50000 (近似), 应落到 level 2: 50000 * 0.10 - 2520 = 2480
        BigDecimal tax = taxService.calculateTax(new BigDecimal("50000"), new BigDecimal("50000"), null);
        assertEquals(0, tax.compareTo(new BigDecimal("2480.00")));
    }

    @Test
    void calculateTax_nullTaxableIncome_returnsZero() {
        BigDecimal tax = taxService.calculateTax(null, BigDecimal.ZERO, null);
        assertEquals(0, tax.compareTo(BigDecimal.ZERO.setScale(2)));
    }

    // ---------- saveRecord: 幂等性 ----------

    @Test
    void saveRecord_newRecord_inserts() {
        when(taxMapper.findBySlip(11L)).thenReturn(null);
        when(taxMapper.insert(any(PayTax.class))).thenAnswer(inv -> {
            PayTax t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });
        PayTax saved = taxService.saveRecord(11L, 200L, "2026-09",
            new BigDecimal("8000"), new BigDecimal("90"),
            new BigDecimal("0.03"), new BigDecimal("8000"),
            new BigDecimal("90"), null);
        assertEquals(99L, saved.getId());
        assertEquals(TID, saved.getTenantId());
        assertEquals(11L, saved.getSlipId());
    }

    @Test
    void saveRecord_existingRecord_updates() {
        PayTax existing = new PayTax();
        existing.setId(99L);
        existing.setTenantId(TID);
        existing.setSlipId(11L);
        existing.setEmployeeId(200L);
        when(taxMapper.findBySlip(11L)).thenReturn(existing);
        taxService.saveRecord(11L, 200L, "2026-09",
            new BigDecimal("9000"), new BigDecimal("100"),
            new BigDecimal("0.03"), new BigDecimal("9000"),
            new BigDecimal("100"), null);
        verify(taxMapper, never()).insert(any(PayTax.class));
        verify(taxMapper, times(1)).updateById(existing);
    }

    // ---------- sumSpecialDeduction 静态工具 ----------

    @Test
    void sumSpecialDeduction_sumsValues() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("children", 1000);
        m.put("mortgage", 1000);
        m.put("elder", 2000);
        BigDecimal sum = TaxService.sumSpecialDeduction(m);
        assertEquals(0, sum.compareTo(new BigDecimal("4000.00")));
    }

    @Test
    void sumSpecialDeduction_nullOrEmpty_returnsZero() {
        assertEquals(0, TaxService.sumSpecialDeduction(null).compareTo(BigDecimal.ZERO));
        assertEquals(0, TaxService.sumSpecialDeduction(java.util.Map.of()).compareTo(BigDecimal.ZERO));
    }

    // ---------- cumulativeIncome: 简化实现使用最新一条 ----------

    @Test
    void cumulativeIncome_emptyRecords_returnsZero() {
        when(taxMapper.listByEmployeeYearTo(any(), any(), any())).thenReturn(List.of());
        BigDecimal sum = taxService.cumulativeIncome(200L, "2026-09");
        assertEquals(0, sum.compareTo(BigDecimal.ZERO));
    }

    @Test
    void cumulativeIncome_returnsLatestRecordCumulative() {
        PayTax t1 = new PayTax();
        t1.setCumulativeIncome(new BigDecimal("10000"));
        PayTax t2 = new PayTax();
        t2.setCumulativeIncome(new BigDecimal("20000"));
        when(taxMapper.listByEmployeeYearTo(eq(200L), any(), eq("2026-09"))).thenReturn(List.of(t1, t2));
        BigDecimal sum = taxService.cumulativeIncome(200L, "2026-09");
        assertEquals(0, sum.compareTo(new BigDecimal("20000")));
    }
}