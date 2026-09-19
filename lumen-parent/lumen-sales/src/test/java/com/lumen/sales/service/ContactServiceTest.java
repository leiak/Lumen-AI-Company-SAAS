package com.lumen.sales.service;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Contact;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.mapper.ContactMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContactService.setPrimary 取消旧的 primary。
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private ContactMapper contactMapper;
    @Mock private CustomerService customerService;
    @InjectMocks private ContactService contactService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long CUSTOMER_ID = 99L;
    private static final long CONTACT_OLD = 10L;
    private static final long CONTACT_NEW = 11L;

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

    private Customer stubCustomer() {
        Customer c = new Customer();
        c.setId(CUSTOMER_ID);
        c.setTenantId(TENANT);
        return c;
    }

    private Contact stubContact(long id, boolean isPrimary) {
        Contact c = new Contact();
        c.setId(id);
        c.setCustomerId(CUSTOMER_ID);
        c.setName(id == CONTACT_OLD ? "old" : "new");
        c.setIsPrimary(isPrimary);
        c.setTenantId(TENANT);
        return c;
    }

    @Test
    void setPrimary_clearsOldPrimaryAndPromotesNew() {
        Contact old = stubContact(CONTACT_OLD, true);
        Contact newC = stubContact(CONTACT_NEW, false);

        when(contactMapper.selectById(CONTACT_NEW)).thenReturn(newC);
        when(contactMapper.findByCustomer(CUSTOMER_ID, TENANT)).thenReturn(List.of(old, newC));
        when(contactMapper.updateById(any(Contact.class))).thenAnswer(inv -> 1);

        Contact out = contactService.setPrimary(CONTACT_NEW);
        assertTrue(Boolean.TRUE.equals(out.getIsPrimary()));

        // 旧 primary 必须被 update 成 false
        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        boolean foundOldCleared = captor.getAllValues().stream()
            .anyMatch(c -> c.getId() == CONTACT_OLD && Boolean.FALSE.equals(c.getIsPrimary()));
        assertTrue(foundOldCleared, "Old primary should be cleared");
    }

    @Test
    void setPrimary_alreadyPrimary_returnsSameContact() {
        Contact existing = stubContact(CONTACT_OLD, true);
        when(contactMapper.selectById(CONTACT_OLD)).thenReturn(existing);
        Contact out = contactService.setPrimary(CONTACT_OLD);
        assertEquals(CONTACT_OLD, out.getId());
        assertTrue(Boolean.TRUE.equals(out.getIsPrimary()));
    }

    @Test
    void findPrimary_returnsContact() {
        Contact primary = stubContact(CONTACT_OLD, true);
        when(customerService.get(CUSTOMER_ID)).thenReturn(stubCustomer());
        when(contactMapper.findPrimary(CUSTOMER_ID, TENANT)).thenReturn(primary);
        Contact out = contactService.findPrimary(CUSTOMER_ID);
        assertEquals(CONTACT_OLD, out.getId());
    }
}