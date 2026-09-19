package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.mapper.CustomerMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 公海/私海 + 等级修改 + 跨租户 404。
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerMapper customerMapper;
    @InjectMocks private CustomerService customerService;

    private static final long UID = 100L;
    private static final long OTHER_UID = 200L;
    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long CUSTOMER_ID = 99L;

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

    private Customer stubCustomer(String status, Long ownerUserId, long tenantId) {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        c.setCode("C-001");
        c.setName("Acme");
        c.setStatus(status);
        c.setOwnerUserId(ownerUserId);
        c.setLevel("B");
        c.setTenantId(tenantId);
        return c;
    }

    @Test
    void claimFromPool_inPool_claimsSuccessfully() {
        Customer c = stubCustomer(CustomerService.STATUS_IN_POOL, null, TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        when(customerMapper.updateById(any(Customer.class))).thenAnswer(inv -> 1);

        Customer claimed = customerService.claimFromPool(CUSTOMER_ID);
        assertEquals(CustomerService.STATUS_PRIVATE, claimed.getStatus());
        assertEquals(UID, claimed.getOwnerUserId());
    }

    @Test
    void claimFromPool_alreadyPrivate_throws409() {
        Customer c = stubCustomer(CustomerService.STATUS_PRIVATE, UID, TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.claimFromPool(CUSTOMER_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void returnToPool_otherUserNotAdmin_throws403() {
        Customer c = stubCustomer(CustomerService.STATUS_PRIVATE, OTHER_UID, TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.returnToPool(CUSTOMER_ID, null));
        assertEquals(403, ex.getCode());
    }

    @Test
    void returnToPool_ownerCanReturn() {
        Customer c = stubCustomer(CustomerService.STATUS_PRIVATE, UID, TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        when(customerMapper.updateById(any(Customer.class))).thenAnswer(inv -> 1);
        Customer r = customerService.returnToPool(CUSTOMER_ID, "low value");
        assertEquals(CustomerService.STATUS_IN_POOL, r.getStatus());
        assertNull(r.getOwnerUserId());
    }

    @Test
    void get_crossTenant_returns404() {
        Customer c = stubCustomer(CustomerService.STATUS_IN_POOL, null, OTHER_TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.get(CUSTOMER_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void get_superAdminCanAccessAnyTenant() {
        Customer c = stubCustomer(CustomerService.STATUS_IN_POOL, null, OTHER_TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        UserContextHolder.set(UserContext.builder()
            .userId(999L).tenantId(TENANT).userName("root")
            .roles(Set.of("super_admin")).build());
        Customer got = customerService.get(CUSTOMER_ID);
        assertEquals(CUSTOMER_ID, got.getId());
    }

    @Test
    void updateLevel_nonAdmin_throws403() {
        // current roles = {sales}
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.updateLevel(CUSTOMER_ID, "A"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void updateLevel_adminCanChange() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("root")
            .roles(Set.of("admin")).build());
        Customer c = stubCustomer(CustomerService.STATUS_PRIVATE, UID, TENANT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(c);
        when(customerMapper.updateById(any(Customer.class))).thenAnswer(inv -> 1);
        Customer out = customerService.updateLevel(CUSTOMER_ID, "A");
        assertEquals("A", out.getLevel());
    }

    @Test
    void updateLevel_invalidLevel_throws400() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("root")
            .roles(Set.of("admin")).build());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.updateLevel(CUSTOMER_ID, "Z"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_duplicateCode_throws409() {
        Customer req = new Customer();
        req.setCode("DUP");
        req.setName("dup");
        when(customerMapper.insert(any(Customer.class)))
            .thenThrow(new DuplicateKeyException("uk_tenant_code"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> customerService.save(req));
        assertEquals(409, ex.getCode());
    }

    @Test
    void allowedLevels_containsABCD() {
        assertEquals(4, customerService.allowedLevels().size());
        assertTrue(customerService.allowedLevels().contains("A"));
        assertTrue(customerService.allowedLevels().contains("D"));
    }

    @Test
    void allowedStatuses_containsFour() {
        assertEquals(4, customerService.allowedStatuses().size());
    }
}