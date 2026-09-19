package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.entity.Lead;
import com.lumen.sales.mapper.CustomerMapper;
import com.lumen.sales.mapper.LeadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private LeadMapper leadMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerService customerService;
    @InjectMocks private LeadService leadService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long LEAD_ID = 99L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("sales")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Lead stubLead(String status) {
        Lead l = new Lead();
        l.setId(LEAD_ID);
        l.setCustomerName("Future Co");
        l.setStatus(status);
        l.setTenantId(TENANT);
        l.setOwnerUserId(UID);
        return l;
    }

    @Test
    void convertToCustomer_createsCustomerAndMarksConverted() {
        Lead l = stubLead(LeadService.STATUS_QUALIFIED);
        when(leadMapper.selectById(LEAD_ID)).thenReturn(l);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(inv -> {
            Customer arg = inv.getArgument(0);
            arg.setId(500L);
            return 1;
        });
        when(leadMapper.updateById(any(Lead.class))).thenAnswer(inv -> 1);

        Customer c = leadService.convertToCustomer(LEAD_ID);
        assertEquals(CustomerService.STATUS_IN_POOL, c.getStatus());
        assertNull(c.getOwnerUserId());
        assertEquals(LeadService.STATUS_CONVERTED, l.getStatus());
        assertEquals(500L, l.getConvertedCustomerId());
    }

    @Test
    void convertToCustomer_alreadyConverted_throws409() {
        Lead l = stubLead(LeadService.STATUS_CONVERTED);
        when(leadMapper.selectById(LEAD_ID)).thenReturn(l);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> leadService.convertToCustomer(LEAD_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void convertToCustomer_lostLead_throws409() {
        Lead l = stubLead(LeadService.STATUS_LOST);
        when(leadMapper.selectById(LEAD_ID)).thenReturn(l);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> leadService.convertToCustomer(LEAD_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void update_convertedLead_throws409() {
        Lead l = stubLead(LeadService.STATUS_CONVERTED);
        when(leadMapper.selectById(LEAD_ID)).thenReturn(l);
        Lead req = new Lead();
        req.setId(LEAD_ID);
        req.setCustomerName("New");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> leadService.update(req));
        assertEquals(409, ex.getCode());
    }
}