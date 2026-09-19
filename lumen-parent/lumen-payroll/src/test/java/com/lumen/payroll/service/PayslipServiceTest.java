package com.lumen.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.CalculatePayrollRequest;
import com.lumen.payroll.dto.ConfirmPayslipRequest;
import com.lumen.payroll.entity.PayEmployeeSalary;
import com.lumen.payroll.entity.PaySalaryStructure;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.entity.PaySlipItem;
import com.lumen.payroll.entity.PayTax;
import com.lumen.payroll.mapper.PaySlipItemMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayslipServiceTest {

    @Mock private PaySlipMapper slipMapper;
    @Mock private PaySlipItemMapper itemMapper;
    @Mock private EmployeeSalaryService employeeSalaryService;
    @Mock private SalaryStructureService structureService;
    @Mock private SocialSecurityService socialSecurityService;
    @Mock private TaxService taxService;

    @InjectMocks private PayslipService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private CalculatePayrollRequest req(String period) {
        CalculatePayrollRequest r = new CalculatePayrollRequest();
        r.setPeriod(period);
        return r;
    }

    private PayEmployeeSalary stubActive(long empId, long structId, String baseSalary) {
        PayEmployeeSalary e = new PayEmployeeSalary();
        e.setId(empId);
        e.setTenantId(TID);
        e.setEmployeeId(empId);
        e.setStructureId(structId);
        e.setBaseSalary(new BigDecimal(baseSalary));
        e.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        e.setStatus("active");
        return e;
    }

    private PaySalaryStructure stubStruct(long id, String baseSalary, Map<String, Object> extra) {
        PaySalaryStructure s = new PaySalaryStructure();
        s.setId(id);
        s.setTenantId(TID);
        s.setCode("STR-A");
        Map<String, Object> c = new HashMap<>();
        c.put("baseSalary", new BigDecimal(baseSalary));
        if (extra != null) c.putAll(extra);
        s.setComponents(c);
        return s;
    }

    // ---------- calculate: 算薪公式 (核心) ----------

    @Test
    void calculate_basicSalary_noAllowance_socialTax() {
        PayEmployeeSalary e = stubActive(200L, 11L, "10000");
        PaySalaryStructure s = stubStruct(11L, "10000", null);

        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of());
        when(employeeSalaryService.listActiveByTenant(TID)).thenReturn(List.of(e));
        when(structureService.getInternal(eq(11L), eq(TID))).thenReturn(s);
        when(socialSecurityService.employeeAmount(200L, "2026-09"))
            .thenReturn(new BigDecimal("800")); // 社保个人部分
        when(taxService.cumulativeIncome(eq(200L), eq("2026-09")))
            .thenReturn(BigDecimal.ZERO);
        when(taxService.calculateTax(any(), any(), any())).thenReturn(new BigDecimal("90"));
        when(slipMapper.insert(any(PaySlip.class))).thenAnswer(inv -> {
            PaySlip sl = inv.getArgument(0);
            sl.setId(77L);
            return 1;
        });

        int created = service.calculate(req("2026-09"));
        assertEquals(1, created);

        ArgumentCaptor<PaySlip> slipCap = ArgumentCaptor.forClass(PaySlip.class);
        verify(slipMapper).insert(slipCap.capture());
        PaySlip sl = slipCap.getValue();
        // gross = 10000 (no allowance)
        assertEquals(0, sl.getGrossSalary().compareTo(new BigDecimal("10000.00")));
        // totalDeduction = 800 + 90 = 890
        assertEquals(0, sl.getTotalDeduction().compareTo(new BigDecimal("890.00")));
        // netSalary = 10000 - 890 = 9110
        assertEquals(0, sl.getNetSalary().compareTo(new BigDecimal("9110.00")));
        assertEquals("calculated", sl.getStatus());
        assertNotNull(sl.getCalculatedAt());

        // item 校验: baseSalary earning + socialEmployee + tax deductions
        ArgumentCaptor<PaySlipItem> itemCap = ArgumentCaptor.forClass(PaySlipItem.class);
        verify(itemMapper, atLeast(3)).insert(itemCap.capture());
        List<PaySlipItem> items = itemCap.getAllValues();
        long earnings = items.stream().filter(i -> "earning".equals(i.getItemType())).count();
        long deductions = items.stream().filter(i -> "deduction".equals(i.getItemType())).count();
        assertEquals(1, earnings);
        assertEquals(2, deductions); // socialEmployee + tax
    }

    @Test
    void calculate_withAllowances_aggregatedIntoGross() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("positionAllowance", 2000);
        extra.put("performanceBonus", 1500);
        PayEmployeeSalary e = stubActive(200L, 11L, "10000");
        PaySalaryStructure s = stubStruct(11L, "10000", extra);

        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of());
        when(employeeSalaryService.listActiveByTenant(TID)).thenReturn(List.of(e));
        when(structureService.getInternal(any(), any())).thenReturn(s);
        when(socialSecurityService.employeeAmount(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.cumulativeIncome(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.calculateTax(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(slipMapper.insert(any(PaySlip.class))).thenAnswer(inv -> {
            PaySlip sl = inv.getArgument(0);
            sl.setId(80L);
            return 1;
        });

        service.calculate(req("2026-09"));
        ArgumentCaptor<PaySlip> cap = ArgumentCaptor.forClass(PaySlip.class);
        verify(slipMapper).insert(cap.capture());
        PaySlip sl = cap.getValue();
        // gross = 10000 + 2000 + 1500 = 13500
        assertEquals(0, sl.getGrossSalary().compareTo(new BigDecimal("13500.00")));
        // totalDeduction = 0
        assertEquals(0, sl.getTotalDeduction().compareTo(BigDecimal.ZERO));
        // netSalary = 13500
        assertEquals(0, sl.getNetSalary().compareTo(new BigDecimal("13500.00")));
    }

    @Test
    void calculate_existingNonDraft_throws409() {
        PaySlip existing = new PaySlip();
        existing.setId(1L);
        existing.setTenantId(TID);
        existing.setStatus("confirmed");
        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of(existing));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.calculate(req("2026-09")));
        assertEquals(409, ex.getCode());
        verify(employeeSalaryService, never()).listActiveByTenant(any());
    }

    @Test
    void calculate_existingDraft_clearedAndRecalculated() {
        PaySlip draft = new PaySlip();
        draft.setId(1L);
        draft.setTenantId(TID);
        draft.setStatus("draft");
        PayEmployeeSalary e = stubActive(200L, 11L, "10000");
        PaySalaryStructure s = stubStruct(11L, "10000", null);

        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of(draft));
        when(employeeSalaryService.listActiveByTenant(TID)).thenReturn(List.of(e));
        when(structureService.getInternal(any(), any())).thenReturn(s);
        when(socialSecurityService.employeeAmount(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.cumulativeIncome(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.calculateTax(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(slipMapper.insert(any(PaySlip.class))).thenAnswer(inv -> {
            PaySlip sl = inv.getArgument(0);
            sl.setId(80L);
            return 1;
        });

        int created = service.calculate(req("2026-09"));
        assertEquals(1, created);
        verify(itemMapper).delete(any(LambdaQueryWrapper.class));
        verify(slipMapper).deleteById(1L);
    }

    @Test
    void calculate_skipsEmployeeOutsideEffectivePeriod() {
        PayEmployeeSalary in = stubActive(200L, 11L, "10000");
        PayEmployeeSalary out = stubActive(300L, 11L, "10000");
        out.setEffectiveFrom(LocalDate.of(2027, 1, 1)); // 未来生效, 不算

        PaySalaryStructure s = stubStruct(11L, "10000", null);

        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of());
        when(employeeSalaryService.listActiveByTenant(TID)).thenReturn(List.of(in, out));
        when(structureService.getInternal(any(), any())).thenReturn(s);
        when(socialSecurityService.employeeAmount(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.cumulativeIncome(any(), any())).thenReturn(BigDecimal.ZERO);
        when(taxService.calculateTax(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(slipMapper.insert(any(PaySlip.class))).thenAnswer(inv -> {
            PaySlip sl = inv.getArgument(0);
            sl.setId(99L);
            return 1;
        });

        int created = service.calculate(req("2026-09"));
        assertEquals(1, created); // 只 200L
        ArgumentCaptor<PaySlip> cap = ArgumentCaptor.forClass(PaySlip.class);
        verify(slipMapper).insert(cap.capture());
        assertEquals(200L, cap.getValue().getEmployeeId());
    }

    // ---------- confirm: 状态机 ----------

    @Test
    void confirm_calculatedSlip_succeeds() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("calculated");
        when(slipMapper.selectById(11L)).thenReturn(s);
        when(itemMapper.findBySlip(11L)).thenReturn(List.of(new PaySlipItem()));
        ConfirmPayslipRequest req = new ConfirmPayslipRequest();
        req.setSlipIds(List.of(11L));
        List<PaySlip> out = service.confirm(req);
        assertEquals(1, out.size());
        assertEquals("confirmed", out.get(0).getStatus());
        assertNotNull(out.get(0).getConfirmedAt());
    }

    @Test
    void confirm_draftSlip_throws409() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("draft");
        when(slipMapper.selectById(11L)).thenReturn(s);
        ConfirmPayslipRequest req = new ConfirmPayslipRequest();
        req.setSlipIds(List.of(11L));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(req));
        assertEquals(409, ex.getCode());
    }

    @Test
    void confirm_noItems_throws409() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("calculated");
        when(slipMapper.selectById(11L)).thenReturn(s);
        when(itemMapper.findBySlip(11L)).thenReturn(List.of());
        ConfirmPayslipRequest req = new ConfirmPayslipRequest();
        req.setSlipIds(List.of(11L));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(req));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("no items"));
    }

    // ---------- markPaid: 状态机 ----------

    @Test
    void markPaid_confirmedSlip_succeeds() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("confirmed");
        when(slipMapper.selectById(11L)).thenReturn(s);
        PaySlip out = service.markPaid(11L);
        assertEquals("paid", out.getStatus());
        assertNotNull(out.getPaidAt());
    }

    @Test
    void markPaid_calculatedSlip_throws409() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("calculated");
        when(slipMapper.selectById(11L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markPaid(11L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("must be confirmed"));
    }

    @Test
    void markPaid_draftSlip_throws409() {
        PaySlip s = new PaySlip();
        s.setId(11L);
        s.setTenantId(TID);
        s.setStatus("draft");
        when(slipMapper.selectById(11L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markPaid(11L));
        assertEquals(409, ex.getCode());
    }

    // ---------- privacy: /pay/slip/my 不接受 query 参数 ----------

    @Test
    void mySlips_employeeIdFromCtxNotQuery() {
        when(slipMapper.findByEmployee(UID)).thenReturn(List.of());
        List<PaySlip> out = service.mySlips();
        assertNotNull(out);
        // ctx.userId = UID; 应被当作 employeeId 查 (非 query param)
        verify(slipMapper).findByEmployee(UID);
    }

    @Test
    void mySlips_filtersCrossTenant() {
        PaySlip mine = new PaySlip();
        mine.setId(1L); mine.setTenantId(TID); mine.setEmployeeId(UID);
        PaySlip other = new PaySlip();
        other.setId(2L); other.setTenantId(999L); other.setEmployeeId(UID);
        when(slipMapper.findByEmployee(UID)).thenReturn(List.of(mine, other));
        List<PaySlip> out = service.mySlips();
        assertEquals(1, out.size());
        assertEquals(TID, out.get(0).getTenantId());
    }

    // ---------- get: 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        PaySlip other = new PaySlip();
        other.setId(11L); other.setTenantId(999L);
        when(slipMapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }
}