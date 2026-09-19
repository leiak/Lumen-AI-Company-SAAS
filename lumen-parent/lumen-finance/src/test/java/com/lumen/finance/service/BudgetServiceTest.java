package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinBudget;
import com.lumen.finance.mapper.FinBudgetItemMapper;
import com.lumen.finance.mapper.FinBudgetMapper;
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
class BudgetServiceTest {

    @Mock private FinBudgetMapper budgetMapper;
    @Mock private FinBudgetItemMapper itemMapper;
    @InjectMocks private BudgetService budgetService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinBudget stubBudget(String period, long dept, long subj, String planned, String used) {
        FinBudget b = new FinBudget();
        b.setId(1L);
        b.setTenantId(TID);
        b.setPeriod(period);
        b.setDepartmentId(dept);
        b.setSubjectId(subj);
        b.setPlannedAmount(new BigDecimal(planned));
        b.setUsedAmount(new BigDecimal(used));
        b.setStatus("open");
        return b;
    }

    @Test
    void check_underBudget_returnsTrue() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(
            stubBudget("2026-01", 10L, 11L, "1000.00", "200.00"));
        assertTrue(budgetService.check("2026-01", 10L, 11L, new BigDecimal("500")));
    }

    @Test
    void check_overBudget_returnsFalse() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(
            stubBudget("2026-01", 10L, 11L, "1000.00", "800.00"));
        assertFalse(budgetService.check("2026-01", 10L, 11L, new BigDecimal("500")));
    }

    @Test
    void check_noBudget_returnsTrue_passthrough() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(null);
        assertTrue(budgetService.check("2026-01", 10L, 11L, new BigDecimal("999999")));
    }

    @Test
    void consume_underBudget_updatesUsedAndReturns() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(
            stubBudget("2026-01", 10L, 11L, "1000.00", "200.00"));
        when(budgetMapper.updateById(any(FinBudget.class))).thenReturn(1);

        FinBudget b = budgetService.consume("2026-01", 10L, 11L, new BigDecimal("300"));

        ArgumentCaptor<FinBudget> cap = ArgumentCaptor.forClass(FinBudget.class);
        verify(budgetMapper).updateById(cap.capture());
        assertEquals(new BigDecimal("500.00"), cap.getValue().getUsedAmount());
    }

    @Test
    void consume_exactPlannedBoundary_passes() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(
            stubBudget("2026-01", 10L, 11L, "1000.00", "500.00"));
        when(budgetMapper.updateById(any(FinBudget.class))).thenReturn(1);
        FinBudget b = budgetService.consume("2026-01", 10L, 11L, new BigDecimal("500"));
        assertEquals(new BigDecimal("1000.00"), b.getUsedAmount());
    }

    @Test
    void consume_exceedsPlanned_throws409() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(
            stubBudget("2026-01", 10L, 11L, "1000.00", "800.00"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> budgetService.consume("2026-01", 10L, 11L, new BigDecimal("500")));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("exceeded"));
        verify(budgetMapper, never()).updateById(any());
    }

    @Test
    void consume_noBudget_throws404() {
        when(budgetMapper.findOne("2026-01", 10L, 11L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> budgetService.consume("2026-01", 10L, 11L, new BigDecimal("100")));
        assertEquals(404, ex.getCode());
    }

    @Test
    void consume_zeroAmount_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> budgetService.consume("2026-01", 10L, 11L, BigDecimal.ZERO));
        assertEquals(400, ex.getCode());
    }
}
