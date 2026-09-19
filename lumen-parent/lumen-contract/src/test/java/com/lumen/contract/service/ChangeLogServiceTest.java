package com.lumen.contract.service;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.ChangeLog;
import com.lumen.contract.mapper.ChangeLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChangeLogService — record inserts a row with the right tenant_id and operator_id.
 */
@ExtendWith(MockitoExtension.class)
class ChangeLogServiceTest {

    @Mock private ChangeLogMapper changeLogMapper;

    @InjectMocks private ChangeLogService changeLogService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
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

    @Test
    void record_insertsWithTenantAndOperator() {
        when(changeLogMapper.insert(any(ChangeLog.class))).thenAnswer(inv -> {
            ChangeLog arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });

        changeLogService.record(CONTRACT_ID, "draft",
            null, Map.of("status", "drafting"), "created");

        ArgumentCaptor<ChangeLog> captor = ArgumentCaptor.forClass(ChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        ChangeLog saved = captor.getValue();
        assertEquals(CONTRACT_ID, saved.getContractId());
        assertEquals("draft", saved.getChangeType());
        assertEquals(UID, saved.getOperatorId());
        assertEquals(TENANT, saved.getTenantId());
        assertNotNull(saved.getOperatedAt());
        assertEquals("created", saved.getComment());
    }

    @Test
    void record_persistsBeforeAndAfterMaps() {
        when(changeLogMapper.insert(any(ChangeLog.class))).thenAnswer(inv -> {
            ChangeLog arg = inv.getArgument(0);
            arg.setId(2L);
            return 1;
        });

        Map<String, Object> before = Map.of("status", "drafting");
        Map<String, Object> after = Map.of("status", "pending_approval");
        changeLogService.record(CONTRACT_ID, "draft", before, after, "submitted");

        ArgumentCaptor<ChangeLog> captor = ArgumentCaptor.forClass(ChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        ChangeLog saved = captor.getValue();
        assertNotNull(saved.getBeforeValue());
        assertNotNull(saved.getAfterValue());
        assertEquals("drafting", saved.getBeforeValue().get("status"));
        assertEquals("pending_approval", saved.getAfterValue().get("status"));
    }
}
