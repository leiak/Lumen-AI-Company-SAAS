package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Attachment;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.mapper.AttachmentMapper;
import com.lumen.contract.mapper.ContractMapper;
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

/**
 * AttachmentService — file_id cross-tenant validation (defensive check via parent contract tenant).
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentMapper attachmentMapper;
    @Mock private ContractMapper contractMapper;

    @InjectMocks private AttachmentService attachmentService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long CONTRACT_ID = 99L;

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

    private Contract stubContract(long tenantId) {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setTenantId(tenantId);
        c.setStatus(ContractService.STATUS_DRAFTING);
        return c;
    }

    @Test
    void attach_sameTenant_persistsAttachment() {
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(stubContract(TENANT));
        when(attachmentMapper.insert(any(Attachment.class))).thenAnswer(inv -> {
            Attachment arg = inv.getArgument(0);
            arg.setId(123L);
            return 1;
        });

        Attachment a = attachmentService.attach(CONTRACT_ID, 999L,
            AttachmentService.TYPE_MAIN_CONTRACT);
        assertEquals(123L, a.getId());
        assertEquals(999L, a.getFileId());
        assertEquals(AttachmentService.TYPE_MAIN_CONTRACT, a.getAttachmentType());
    }

    @Test
    void attach_crossTenantContract_throws404() {
        // Attacker tries to attach to a contract belonging to another tenant.
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(stubContract(OTHER_TENANT));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> attachmentService.attach(CONTRACT_ID, 999L, AttachmentService.TYPE_OTHER));
        assertEquals(404, ex.getCode());
    }

    @Test
    void attach_contractNotFound_throws404() {
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> attachmentService.attach(CONTRACT_ID, 999L, AttachmentService.TYPE_OTHER));
        assertEquals(404, ex.getCode());
    }

    @Test
    void attach_nullFileId_throws400() {
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(stubContract(TENANT));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> attachmentService.attach(CONTRACT_ID, null, AttachmentService.TYPE_OTHER));
        assertEquals(400, ex.getCode());
    }
}
