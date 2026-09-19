package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.PaymentRecord;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.mapper.PaymentRecordMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * recordPayment: 累加 collected_amount + status 自动转换。
 * pending → partial → collected;amount 超限 → 409。
 */
@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    @Mock private ReceivableMapper receivableMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private CustomerService customerService;

    @InjectMocks private ReceivableService receivableService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long REC_ID = 99L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("finance")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Receivable stubReceivable(String status, String amount, String collected) {
        Receivable r = new Receivable();
        r.setId(REC_ID);
        r.setCode("R-001");
        r.setAmount(new BigDecimal(amount));
        r.setCollectedAmount(new BigDecimal(collected));
        r.setStatus(status);
        r.setDueDate(LocalDate.now().plusDays(30));
        r.setTenantId(TENANT);
        return r;
    }

    @Test
    void recordPayment_partial_setsStatusPartial() {
        Receivable r = stubReceivable(ReceivableService.STATUS_PENDING, "1000", "0");
        when(receivableMapper.selectById(REC_ID)).thenReturn(r);
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> {
            PaymentRecord arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });
        when(receivableMapper.updateById(any(Receivable.class))).thenAnswer(inv -> 1);

        PaymentRecord pr = receivableService.recordPayment(REC_ID, new BigDecimal("300"), "bank", "first");
        assertEquals(new BigDecimal("300"), pr.getAmount());
        assertEquals(new BigDecimal("300"), r.getCollectedAmount());
        assertEquals(ReceivableService.STATUS_PARTIAL, r.getStatus());
    }

    @Test
    void recordPayment_full_setsStatusCollected() {
        Receivable r = stubReceivable(ReceivableService.STATUS_PARTIAL, "1000", "700");
        when(receivableMapper.selectById(REC_ID)).thenReturn(r);
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> {
            PaymentRecord arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });
        when(receivableMapper.updateById(any(Receivable.class))).thenAnswer(inv -> 1);

        receivableService.recordPayment(REC_ID, new BigDecimal("300"), "bank", "final");
        assertEquals(new BigDecimal("1000"), r.getCollectedAmount());
        assertEquals(ReceivableService.STATUS_COLLECTED, r.getStatus());
    }

    @Test
    void recordPayment_exceedAmount_throws409() {
        Receivable r = stubReceivable(ReceivableService.STATUS_PARTIAL, "1000", "900");
        when(receivableMapper.selectById(REC_ID)).thenReturn(r);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.recordPayment(REC_ID, new BigDecimal("200"), "bank", null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void recordPayment_zeroAmount_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.recordPayment(REC_ID, BigDecimal.ZERO, "bank", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void recordPayment_overdue_setsStatusOverdue() {
        // 已过期 + 未收齐 → overdue
        Receivable r = stubReceivable(ReceivableService.STATUS_PARTIAL, "1000", "500");
        r.setDueDate(LocalDate.now().minusDays(1));
        when(receivableMapper.selectById(REC_ID)).thenReturn(r);
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> 1);
        when(receivableMapper.updateById(any(Receivable.class))).thenAnswer(inv -> 1);

        receivableService.recordPayment(REC_ID, new BigDecimal("100"), "bank", null);
        assertEquals(ReceivableService.STATUS_OVERDUE, r.getStatus());
    }

    @Test
    void get_crossTenant_returns404() {
        Receivable r = stubReceivable(ReceivableService.STATUS_PENDING, "1000", "0");
        r.setTenantId(999L);
        when(receivableMapper.selectById(REC_ID)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receivableService.get(REC_ID));
        assertEquals(404, ex.getCode());
    }
}