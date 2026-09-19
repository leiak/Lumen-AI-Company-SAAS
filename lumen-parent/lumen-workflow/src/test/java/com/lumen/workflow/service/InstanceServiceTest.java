package com.lumen.workflow.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.mapper.WfInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * InstanceService delegates startInstance to {@link EngineService} and routes cancel()
 * through {@link EngineService#closeInstanceCancelled}. These tests verify the delegation
 * and the cancel guards (running state) — the engine internals are tested elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock private WfInstanceMapper instanceMapper;
    @Mock private EngineService engineService;

    @InjectMocks private InstanceService instanceService;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // start()
    // ---------------------------------------------------------------

    @Test
    void startInstance_happyPath_returnsInstanceId() {
        when(engineService.startInstance("leave", "BIZ-1", null)).thenReturn(99L);

        Long instanceId = instanceService.start("leave", "BIZ-1", null);

        assertEquals(99L, instanceId);
        verify(engineService).startInstance("leave", "BIZ-1", null);
    }

    @Test
    void startInstance_withVariables_propagatesToEngine() {
        when(engineService.startInstance(eq("expense"), eq("E-1"), any())).thenReturn(101L);

        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 500);
        Long id = instanceService.start("expense", "E-1", vars);

        assertEquals(101L, id);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(engineService).startInstance(eq("expense"), eq("E-1"), captor.capture());
        assertEquals(500, captor.getValue().get("amount"));
    }

    @Test
    void startInstance_definitionNotFound_throws404() {
        doThrow(new ServiceException(404, "Process definition not found: missing"))
            .when(engineService).startInstance(eq("missing"), eq("B"), any());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.start("missing", "B", null));
        assertEquals(404, ex.getCode());
    }

    @Test
    void startInstance_definitionNotPublished_throws409() {
        doThrow(new ServiceException(409, "Process definition not published: leave"))
            .when(engineService).startInstance(eq("leave"), eq("B"), any());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.start("leave", "B", null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void startInstance_duplicateBusinessKey_throws409() {
        doThrow(new ServiceException(409, "Active instance already exists for businessKey=B"))
            .when(engineService).startInstance(eq("leave"), eq("B"), any());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.start("leave", "B", null));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // cancel()
    // ---------------------------------------------------------------

    @Test
    void cancel_happyPath_setsStatusCancelled() {
        WfInstance instance = new WfInstance();
        instance.setId(42L);
        instance.setStatus(EngineService.INSTANCE_STATUS_RUNNING);
        when(instanceMapper.selectById(42L)).thenReturn(instance);

        // The engine mutates the instance to CANCELLED — mirror that here.
        doAnswer(inv -> {
            WfInstance arg = inv.getArgument(0);
            arg.setStatus(EngineService.INSTANCE_STATUS_CANCELLED);
            arg.setEndTime(java.time.LocalDateTime.now());
            return null;
        }).when(engineService).closeInstanceCancelled(eq(instance), any());

        WfInstance result = instanceService.cancel(42L, "user changed mind");

        assertEquals(EngineService.INSTANCE_STATUS_CANCELLED, result.getStatus());
        assertNotNull(result.getEndTime());
        verify(engineService).closeInstanceCancelled(instance, "user changed mind");
    }

    @Test
    void cancel_alreadyCompleted_throws409() {
        WfInstance instance = new WfInstance();
        instance.setId(42L);
        instance.setStatus(EngineService.INSTANCE_STATUS_COMPLETED);
        when(instanceMapper.selectById(42L)).thenReturn(instance);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.cancel(42L, "too late"));
        assertEquals(409, ex.getCode());
        verify(engineService, never()).closeInstanceCancelled(any(), any());
    }

    @Test
    void cancel_alreadyCancelled_throws409() {
        WfInstance instance = new WfInstance();
        instance.setId(42L);
        instance.setStatus(EngineService.INSTANCE_STATUS_CANCELLED);
        when(instanceMapper.selectById(42L)).thenReturn(instance);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.cancel(42L, "dup"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void cancel_instanceNotFound_throws404() {
        when(instanceMapper.selectById(999L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> instanceService.cancel(999L, "missing"));
        assertEquals(404, ex.getCode());
    }
}