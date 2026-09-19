package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.dto.PaymentPlanDto;
import com.lumen.contract.entity.PaymentPlan;
import com.lumen.contract.mapper.PaymentPlanMapper;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PaymentPlanService — focused on the markCompleted validation chain:
 *   1) actualAmount > 0
 *   2) actualAmount <= plannedAmount (security: never over-collect)
 *   3) status flipped to completed (==) or partial (<)
 */
@ExtendWith(MockitoExtension.class)
class PaymentPlanServiceTest {

    @Mock private PaymentPlanMapper paymentPlanMapper;
    @Mock private ContractService contractService;

    @InjectMocks private PaymentPlanService paymentPlanService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private PaymentPlan stubPlan(Long id, BigDecimal planned) {
        PaymentPlan p = new PaymentPlan();
        p.setId(id);
        p.setContractId(50L);
        p.setPlannedAmount(planned);
        p.setPlannedDate(LocalDate.now().plusDays(7));
        p.setStatus(PaymentPlanService.STATUS_PENDING);
        p.setTenantId(TENANT);
        return p;
    }

    // ---------------------------------------------------------------
    // generatePlan
    // ---------------------------------------------------------------

    @Test
    void generatePlan_persistsAllPlans() {
        PaymentPlanDto a = new PaymentPlanDto();
        a.setPlanNo("P-1");
        a.setPlannedAmount(new BigDecimal("100.00"));
        a.setPlannedDate(LocalDate.now().plusDays(7));

        PaymentPlanDto b = new PaymentPlanDto();
        b.setPlanNo("P-2");
        b.setPlannedAmount(new BigDecimal("200.00"));
        b.setPlannedDate(LocalDate.now().plusDays(14));

        int n = paymentPlanService.generatePlan(50L, List.of(a, b));
        assertEquals(2, n);
        verify(paymentPlanMapper, times(2)).insert(any(PaymentPlan.class));
    }

    @Test
    void generatePlan_negativeAmount_throws400() {
        PaymentPlanDto a = new PaymentPlanDto();
        a.setPlanNo("P-1");
        a.setPlannedAmount(new BigDecimal("-1"));
        a.setPlannedDate(LocalDate.now());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentPlanService.generatePlan(50L, List.of(a)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void generatePlan_empty_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentPlanService.generatePlan(50L, List.of()));
        assertEquals(400, ex.getCode());
    }

    // ---------------------------------------------------------------
    // markCompleted — the validation chain
    // ---------------------------------------------------------------

    @Test
    void markCompleted_fullAmount_setsStatusCompleted() {
        PaymentPlan p = stubPlan(1L, new BigDecimal("1000.00"));
        when(paymentPlanMapper.selectById(1L)).thenReturn(p);

        PaymentPlan result = paymentPlanService.markCompleted(1L,
            new BigDecimal("1000.00"), LocalDate.now());
        assertEquals(PaymentPlanService.STATUS_COMPLETED, result.getStatus());
        assertEquals(new BigDecimal("1000.00"), result.getActualAmount());
    }

    @Test
    void markCompleted_partialAmount_setsStatusPartial() {
        PaymentPlan p = stubPlan(1L, new BigDecimal("1000.00"));
        when(paymentPlanMapper.selectById(1L)).thenReturn(p);

        PaymentPlan result = paymentPlanService.markCompleted(1L,
            new BigDecimal("400.00"), LocalDate.now());
        assertEquals(PaymentPlanService.STATUS_PARTIAL, result.getStatus());
        assertEquals(new BigDecimal("400.00"), result.getActualAmount());
    }

    @Test
    void markCompleted_overAmount_throws400() {
        PaymentPlan p = stubPlan(1L, new BigDecimal("1000.00"));
        when(paymentPlanMapper.selectById(1L)).thenReturn(p);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentPlanService.markCompleted(1L,
                new BigDecimal("1500.00"), LocalDate.now()));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("exceed"));
        // No update should have been made
        verify(paymentPlanMapper, never()).updateById(any());
    }

    @Test
    void markCompleted_zeroAmount_throws400() {
        // Zero amount is rejected BEFORE the mapper is touched, so we intentionally
        // do not stub selectById.
        ServiceException ex = assertThrows(ServiceException.class,
            () -> paymentPlanService.markCompleted(1L,
                BigDecimal.ZERO, LocalDate.now()));
        assertEquals(400, ex.getCode());
    }

    @Test
    void markCompleted_neverModifiesPlannedAmount() {
        // Verify planned_amount is preserved (immutable after creation).
        PaymentPlan p = stubPlan(1L, new BigDecimal("1000.00"));
        when(paymentPlanMapper.selectById(1L)).thenReturn(p);

        paymentPlanService.markCompleted(1L, new BigDecimal("500.00"), LocalDate.now());

        ArgumentCaptor<PaymentPlan> captor = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(paymentPlanMapper).updateById(captor.capture());
        PaymentPlan saved = captor.getValue();
        assertEquals(new BigDecimal("1000.00"), saved.getPlannedAmount(),
            "planned_amount must remain immutable after creation");
    }

    // ---------------------------------------------------------------
    // findOverdue (Quartz entry point)
    // ---------------------------------------------------------------

    @Test
    void findOverdue_flipsStatusToOverdue() {
        PaymentPlan p1 = stubPlan(1L, new BigDecimal("100"));
        PaymentPlan p2 = stubPlan(2L, new BigDecimal("200"));
        when(paymentPlanMapper.findOverdue(any(LocalDate.class), eq(TENANT)))
            .thenReturn(List.of(p1, p2));

        List<PaymentPlan> result = paymentPlanService.findOverdue();
        assertEquals(2, result.size());
        assertEquals(PaymentPlanService.STATUS_OVERDUE, p1.getStatus());
        assertEquals(PaymentPlanService.STATUS_OVERDUE, p2.getStatus());
        verify(paymentPlanMapper, times(2)).updateById(any());
    }
}
