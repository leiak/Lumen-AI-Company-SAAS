package com.lumen.bi.service;

import com.lumen.bi.dto.SaveReportRequest;
import com.lumen.bi.entity.BiReport;
import com.lumen.bi.mapper.BiReportMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private BiReportMapper reportMapper;
    @InjectMocks private ReportService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private SaveReportRequest req() {
        SaveReportRequest r = new SaveReportRequest();
        r.setCode("SALES_MONTHLY");
        r.setName("销售月报");
        r.setSchedule("daily");
        r.setCronExpression("0 0 8 * * *");
        r.setFormat("pdf");
        r.setRecipients(List.of("alice@example.com"));
        r.setTemplate(new HashMap<>());
        return r;
    }

    private BiReport stub(long id) {
        BiReport r = new BiReport();
        r.setId(id);
        r.setTenantId(TID);
        r.setCode("SALES_MONTHLY");
        r.setStatus("active");
        r.setSchedule("daily");
        r.setCronExpression("0 0 8 * * *");
        r.setFormat("pdf");
        return r;
    }

    // ---------- save: cron 校验 ----------

    @Test
    void save_dailyWithoutCron_throws400() {
        SaveReportRequest r = req();
        r.setCronExpression(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("cronExpression"));
        verify(reportMapper, never()).insert(any());
    }

    @Test
    void save_invalidCron_throws400() {
        SaveReportRequest r = req();
        r.setCronExpression("not-a-cron");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_cronTooFrequent_throws400() {
        SaveReportRequest r = req();
        r.setCronExpression("0 * * * * *"); // 每分钟
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("frequency too high"));
    }

    @Test
    void save_manualSchedule_noCronNeeded() {
        SaveReportRequest r = req();
        r.setSchedule("manual");
        r.setCronExpression(null);
        when(reportMapper.findByCodeAndTenant("SALES_MONTHLY", TID)).thenReturn(null);
        when(reportMapper.insert(any(BiReport.class))).thenAnswer(inv -> {
            BiReport b = inv.getArgument(0);
            b.setId(10L);
            return 1;
        });
        BiReport out = service.save(r);
        assertEquals("manual", out.getSchedule());
    }

    @Test
    void save_validCron_inserts() {
        when(reportMapper.findByCodeAndTenant("SALES_MONTHLY", TID)).thenReturn(null);
        when(reportMapper.insert(any(BiReport.class))).thenAnswer(inv -> {
            BiReport b = inv.getArgument(0);
            b.setId(10L);
            return 1;
        });
        BiReport out = service.save(req());
        assertEquals(10L, out.getId());
    }

    // ---------- runNow ----------

    @Test
    void runNow_invalidFormat_throws400() {
        when(reportMapper.selectById(11L)).thenReturn(stub(11L));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.runNow(11L, "docx"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void runNow_inactiveReport_throws409() {
        BiReport r = stub(11L);
        r.setStatus("inactive");
        when(reportMapper.selectById(11L)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.runNow(11L, "pdf"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void runNow_activePdf_setsLastRunAt() {
        when(reportMapper.selectById(11L)).thenReturn(stub(11L));
        var result = service.runNow(11L, "pdf");
        assertEquals(11L, result.get("reportId"));
        assertEquals("pdf", result.get("format"));
        assertNotNull(result.get("executedAt"));
        verify(reportMapper).updateById(any(BiReport.class));
    }

    // ---------- schedule ----------

    @Test
    void schedule_manual_throws409() {
        BiReport r = stub(11L);
        r.setSchedule("manual");
        r.setCronExpression(null);
        when(reportMapper.selectById(11L)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.schedule(11L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void schedule_validCron_returnsNextFire() {
        BiReport r = stub(11L);
        r.setSchedule("daily");
        r.setCronExpression("0 0 8 * * *");
        when(reportMapper.selectById(11L)).thenReturn(r);
        var out = service.schedule(11L);
        assertEquals(11L, out.get("reportId"));
        assertEquals("daily", out.get("schedule"));
        assertNotNull(out.get("nextFireTime"));
    }

    // ---------- get: 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        BiReport other = stub(11L);
        other.setTenantId(999L);
        when(reportMapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }
}