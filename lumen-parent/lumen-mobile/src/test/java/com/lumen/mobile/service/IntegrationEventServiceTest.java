package com.lumen.mobile.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.IntegrationEventLog;
import com.lumen.mobile.mapper.IntegrationEventLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationEventServiceTest {

    @Mock private IntegrationEventLogMapper mapper;

    @InjectMocks private IntegrationEventService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private IntegrationEventLog mkEvent(Long id, int processed, int retry) {
        IntegrationEventLog e = new IntegrationEventLog();
        e.setId(id);
        e.setTenantId(TID);
        e.setPlatform("dingtalk");
        e.setEventType("bpms_instance_change");
        e.setSourceId("evt-" + id);
        e.setProcessed(processed);
        e.setRetryCount(retry);
        return e;
    }

    // ----- 防重放 + 仅追加 (安全要求 #6 / #13) -----

    @Test
    void receiveEvent_newEvent_insertsPending() {
        when(mapper.findBySource("dingtalk", "evt-1")).thenReturn(null);
        when(mapper.insert(any(IntegrationEventLog.class))).thenAnswer(inv -> {
            IntegrationEventLog e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        });
        Map<String, Object> body = new HashMap<>();
        body.put("foo", "bar");
        IntegrationEventLog out = service.receiveEvent("dingtalk", "bpms_instance_change", "evt-1", body);
        assertEquals(100L, out.getId());
        assertEquals(IntegrationEventService.PROCESSED_PENDING, out.getProcessed());
        assertEquals(0, out.getRetryCount());
    }

    @Test
    void receiveEvent_duplicateSource_returnsExisting() {
        IntegrationEventLog existing = mkEvent(99L, 1, 0);
        when(mapper.findBySource("dingtalk", "evt-1")).thenReturn(existing);

        IntegrationEventLog out = service.receiveEvent("dingtalk", "bpms_instance_change", "evt-1", null);

        assertEquals(99L, out.getId());
        verify(mapper, never()).insert(any());
    }

    @Test
    void receiveEvent_dbUniqueConflict_returnsExisting() {
        when(mapper.findBySource("dingtalk", "evt-1"))
            .thenReturn(null) // first call (pre-check)
            .thenReturn(mkEvent(77L, 1, 0)); // after UNIQUE conflict
        when(mapper.insert(any(IntegrationEventLog.class)))
            .thenThrow(new DuplicateKeyException("uk_int_event_source_type"));

        IntegrationEventLog out = service.receiveEvent("dingtalk", "bpms_instance_change", "evt-1", null);

        assertEquals(77L, out.getId());
    }

    @Test
    void receiveEvent_noSourceId_insertsWithoutDedupeCheck() {
        when(mapper.insert(any(IntegrationEventLog.class))).thenAnswer(inv -> {
            IntegrationEventLog e = inv.getArgument(0);
            e.setId(101L);
            return 1;
        });
        IntegrationEventLog out = service.receiveEvent("dingtalk", "evt-type", null, null);
        assertEquals(101L, out.getId());
        verify(mapper, never()).findBySource(anyString(), anyString());
    }

    @Test
    void receiveEvent_emptyPlatform_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.receiveEvent("", "evt", "src", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void receiveEvent_emptyEventType_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.receiveEvent("dingtalk", "", "src", null));
        assertEquals(400, ex.getCode());
    }

    // ----- processEvent 状态位 -----

    @Test
    void processEvent_success_setsProcessedOne() {
        IntegrationEventLog e = mkEvent(11L, 0, 0);
        when(mapper.selectById(11L)).thenReturn(e);
        IntegrationEventLog out = service.processEvent(11L, true, null);
        assertEquals(IntegrationEventService.PROCESSED_OK, out.getProcessed());
        assertNotNull(out.getProcessedAt());
        assertNull(out.getErrorMessage());
        verify(mapper).updateById(e);
    }

    @Test
    void processEvent_failure_incrementsRetryCount() {
        IntegrationEventLog e = mkEvent(11L, 0, 0);
        when(mapper.selectById(11L)).thenReturn(e);
        IntegrationEventLog out = service.processEvent(11L, false, "network error");
        assertEquals(IntegrationEventService.PROCESSED_FAILED, out.getProcessed());
        assertEquals(1, out.getRetryCount());
        assertEquals("network error", out.getErrorMessage());
    }

    @Test
    void processEvent_idempotentAlreadyOk_noOp() {
        IntegrationEventLog e = mkEvent(11L, 1, 0); // already OK
        when(mapper.selectById(11L)).thenReturn(e);
        service.processEvent(11L, true, null);
        verify(mapper, never()).updateById(any());
    }

    @Test
    void processEvent_missing_throws404() {
        when(mapper.selectById(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.processEvent(99L, true, null));
        assertEquals(404, ex.getCode());
    }

    @Test
    void processEvent_truncatesErrorMessage() {
        IntegrationEventLog e = mkEvent(11L, 0, 0);
        when(mapper.selectById(11L)).thenReturn(e);
        String big = "x".repeat(IntegrationEventService.DEFAULT_ERROR_MESSAGE_MAX + 100);
        IntegrationEventLog out = service.processEvent(11L, false, big);
        assertEquals(IntegrationEventService.DEFAULT_ERROR_MESSAGE_MAX,
            out.getErrorMessage().length());
    }

    // ----- retryFailed -----

    @Test
    void retryFailed_resetsFailedUnderMax() {
        IntegrationEventLog f1 = mkEvent(11L, 2, 1); // failed, retry 1 < max 3
        IntegrationEventLog f2 = mkEvent(12L, 2, 3); // failed, retry 3 = max 3 → 不重试
        IntegrationEventLog p1 = mkEvent(13L, 0, 0); // pending → 不动
        when(mapper.findUnprocessed(500)).thenReturn(List.of(f1, f2, p1));

        List<Long> out = service.retryFailed(3);

        assertEquals(List.of(11L), out);
        assertEquals(IntegrationEventService.PROCESSED_PENDING, f1.getProcessed());
        verify(mapper).updateById(f1);
        verify(mapper, never()).updateById(f2);
        verify(mapper, never()).updateById(p1);
    }

    @Test
    void retryFailed_noTenant_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () -> service.retryFailed(3));
        assertEquals(401, ex.getCode());
    }

    // ----- security: ctx required -----

    @Test
    void processEvent_noTenant_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.processEvent(11L, true, null));
        assertEquals(401, ex.getCode());
    }

    @Test
    void receiveEvent_noTenantAllowed() {
        // receiveEvent 是公开回调入口 (callback URL 由 controller 暴露, 安全要求 #5 验签)
        // service 层不强制 tenant
        UserContextHolder.clear();
        when(mapper.insert(any(IntegrationEventLog.class))).thenAnswer(inv -> {
            IntegrationEventLog e = inv.getArgument(0);
            e.setId(200L);
            return 1;
        });
        IntegrationEventLog out = service.receiveEvent("dingtalk", "evt", "src", null);
        assertEquals(200L, out.getId());
    }

    private static String anyString() { return org.mockito.ArgumentMatchers.anyString(); }
}
