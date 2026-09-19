package com.lumen.workflow.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.entity.WfTask;
import com.lumen.workflow.entity.WfTaskHistory;
import com.lumen.workflow.mapper.WfInstanceMapper;
import com.lumen.workflow.mapper.WfTaskHistoryMapper;
import com.lumen.workflow.mapper.WfTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private WfTaskMapper taskMapper;
    @Mock private WfTaskHistoryMapper taskHistoryMapper;
    @Mock private WfInstanceMapper instanceMapper;
    @Mock private EngineService engineService;

    @InjectMocks private TaskService taskService;

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

    @Test
    void done_happyPath_invokesEngineWithDone() {
        Long taskId = 1L;
        taskService.done(taskId, "ok");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(engineService).completeTask(eq(taskId), eq(EngineService.ACTION_DONE),
            eq("ok"), captor.capture());
        // done passes null params
        assertNull(captor.getValue());
    }

    @Test
    void transfer_createsNewTaskAtToUserId() {
        Long taskId = 1L;
        Long toUserId = 99L;
        doNothing().when(engineService).completeTask(eq(taskId), eq(EngineService.ACTION_TRANSFER),
            eq("handoff"), any());

        taskService.transfer(taskId, toUserId, "handoff");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(engineService).completeTask(eq(taskId), eq(EngineService.ACTION_TRANSFER),
            eq("handoff"), captor.capture());
        assertEquals(toUserId, captor.getValue().get("toUserId"));
    }

    @Test
    void transfer_withoutToUserId_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.transfer(1L, null, "x"));
        assertEquals(400, ex.getCode());
        verify(engineService, never()).completeTask(anyLong(), any(), any(), any());
    }

    @Test
    void addSign_createsTasksForEachUser() {
        Long taskId = 1L;
        List<Long> userIds = new ArrayList<>();
        userIds.add(11L);
        userIds.add(22L);
        userIds.add(33L);

        taskService.addSign(taskId, userIds, "need extra eyes");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(engineService).completeTask(eq(taskId), eq(EngineService.ACTION_ADD_SIGN),
            eq("need extra eyes"), captor.capture());
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) captor.getValue().get("userIds");
        assertEquals(3, ids.size());
        assertEquals(11L, ids.get(0).longValue());
        assertEquals(22L, ids.get(1).longValue());
        assertEquals(33L, ids.get(2).longValue());
    }

    @Test
    void addSign_withEmptyUserIds_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.addSign(1L, new ArrayList<>(), "x"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void addSign_withNullUserIds_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.addSign(1L, null, "x"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void reject_withValidTarget_invokesEngine() {
        Long taskId = 1L;
        taskService.reject(taskId, "previous_node", "incorrect data");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(engineService).completeTask(eq(taskId), eq(EngineService.ACTION_REJECT),
            eq("incorrect data"), captor.capture());
        assertEquals("previous_node", captor.getValue().get("targetNodeKey"));
    }

    @Test
    void reject_withoutTarget_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.reject(1L, null, "x"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void reject_withBlankTarget_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.reject(1L, "  ", "x"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void history_returnsMappedList() {
        WfInstance instance = new WfInstance();
        instance.setId(99L);
        instance.setTenantId(TID);
        when(instanceMapper.selectById(99L)).thenReturn(instance);

        WfTaskHistory t = new WfTaskHistory();
        t.setId(1L);
        t.setInstanceId(99L);
        t.setAction("done");
        when(taskHistoryMapper.listByInstanceId(99L)).thenReturn(List.of(t));

        List<WfTaskHistory> out = taskService.history(99L);

        assertEquals(1, out.size());
        assertEquals("done", out.get(0).getAction());
        verify(taskHistoryMapper).listByInstanceId(99L);
    }

    @Test
    void history_crossTenant_returns404NotForbidden() {
        WfInstance instance = new WfInstance();
        instance.setId(99L);
        instance.setTenantId(2L); // different tenant
        when(instanceMapper.selectById(99L)).thenReturn(instance);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.history(99L));
        assertEquals(404, ex.getCode());
        verify(taskHistoryMapper, never()).listByInstanceId(any());
    }

    @Test
    void history_instanceNotFound_throws404() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> taskService.history(99L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void findOpenTask_returnsUnderlyingMapper() {
        WfTask t = new WfTask();
        t.setId(5L);
        t.setStatus(EngineService.TASK_STATUS_TODO);
        when(taskMapper.selectById(5L)).thenReturn(t);

        WfTask out = taskService.findOpenTask(5L);

        assertNotNull(out);
        assertEquals(5L, out.getId());
    }
}