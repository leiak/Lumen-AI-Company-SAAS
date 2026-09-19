package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.entity.Statement;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import com.lumen.sales.mapper.StatementMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 对账单 generate 汇总 + 重复期间检查 + send/confirm 状态机。
 */
@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock private StatementMapper statementMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private ReceivableMapper receivableMapper;
    @Mock private CustomerService customerService;

    @InjectMocks private StatementService statementService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long CUSTOMER_ID = 88L;

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

    private Customer stubCustomer() {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        c.setCode("C-001");
        c.setName("Acme");
        c.setTenantId(TENANT);
        c.setStatus(CustomerService.STATUS_ACTIVE);
        return c;
    }

    @Test
    void generate_aggregatesReceivables() {
        when(customerService.get(CUSTOMER_ID)).thenReturn(stubCustomer());
        when(statementMapper.findByCustomerAndPeriod(eq(CUSTOMER_ID), any(), any(), eq(TENANT)))
            .thenReturn(null);
        when(receivableMapper.sumAmountByCustomer(CUSTOMER_ID, TENANT))
            .thenReturn(new BigDecimal("5000"));
        when(receivableMapper.sumCollectedByCustomer(CUSTOMER_ID, TENANT))
            .thenReturn(new BigDecimal("3000"));
        when(orderMapper.findByCustomer(eq(CUSTOMER_ID), any(), eq(TENANT)))
            .thenReturn(List.of());
        when(statementMapper.insert(any(Statement.class))).thenAnswer(inv -> {
            Statement arg = inv.getArgument(0);
            arg.setId(501L);
            return 1;
        });

        Statement s = statementService.generate(CUSTOMER_ID,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertNotNull(s.getId());
        assertEquals(CUSTOMER_ID, s.getCustomerId());
        assertEquals(new BigDecimal("5000"), s.getTotalAmount());
        assertEquals(StatementService.STATUS_DRAFT, s.getStatus());
    }

    @Test
    void generate_duplicatePeriod_throws409() {
        when(customerService.get(CUSTOMER_ID)).thenReturn(stubCustomer());
        Statement existing = new Statement();
        existing.setId(1L);
        when(statementMapper.findByCustomerAndPeriod(eq(CUSTOMER_ID), any(), any(), eq(TENANT)))
            .thenReturn(existing);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> statementService.generate(CUSTOMER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertEquals(409, ex.getCode());
    }

    @Test
    void generate_periodEndBeforeStart_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> statementService.generate(CUSTOMER_ID,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void send_draftStatement_flipsToSent() {
        Statement s = new Statement();
        s.setId(1L);
        s.setStatus(StatementService.STATUS_DRAFT);
        s.setTenantId(TENANT);
        when(statementMapper.selectById(1L)).thenReturn(s);
        when(statementMapper.updateById(any(Statement.class))).thenAnswer(inv -> 1);
        Statement out = statementService.send(1L);
        assertEquals(StatementService.STATUS_SENT, out.getStatus());
        assertNotNull(out.getSentAt());
    }

    @Test
    void send_alreadySent_throws409() {
        Statement s = new Statement();
        s.setId(1L);
        s.setStatus(StatementService.STATUS_SENT);
        s.setTenantId(TENANT);
        when(statementMapper.selectById(1L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> statementService.send(1L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void confirm_sentStatement_flipsToConfirmed() {
        Statement s = new Statement();
        s.setId(1L);
        s.setStatus(StatementService.STATUS_SENT);
        s.setTenantId(TENANT);
        when(statementMapper.selectById(1L)).thenReturn(s);
        when(statementMapper.updateById(any(Statement.class))).thenAnswer(inv -> 1);
        Statement out = statementService.confirm(1L);
        assertEquals(StatementService.STATUS_CONFIRMED, out.getStatus());
    }

    @Test
    void confirm_draftStatement_throws409() {
        Statement s = new Statement();
        s.setId(1L);
        s.setStatus(StatementService.STATUS_DRAFT);
        s.setTenantId(TENANT);
        when(statementMapper.selectById(1L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> statementService.confirm(1L));
        assertEquals(409, ex.getCode());
    }
}