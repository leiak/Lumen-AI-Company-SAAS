package com.lumen.bi.service;

import com.lumen.bi.dto.SaveDashboardRequest;
import com.lumen.bi.entity.BiDashboard;
import com.lumen.bi.mapper.BiDashboardMapper;
import com.lumen.bi.mapper.BiDatasetMapper;
import com.lumen.bi.mapper.BiWidgetMapper;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private BiDashboardMapper dashboardMapper;
    @Mock private BiWidgetMapper widgetMapper;
    @Mock private BiDatasetMapper datasetMapper;
    @Mock private PermissionService permissionService;
    @InjectMocks private DashboardService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(Set.of("user"))
            .build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private SaveDashboardRequest req() {
        SaveDashboardRequest r = new SaveDashboardRequest();
        r.setCode("SALES_OVERVIEW");
        r.setName("销售总览");
        r.setLayout(new HashMap<>());
        return r;
    }

    private BiDashboard stub(long id, String status) {
        BiDashboard d = new BiDashboard();
        d.setId(id);
        d.setTenantId(TID);
        d.setCode("SALES_OVERVIEW");
        d.setName("销售总览");
        d.setStatus(status);
        d.setLayout(new HashMap<>());
        return d;
    }

    // ---------- save: 状态机 ----------

    @Test
    void save_published_throws409() {
        BiDashboard existing = stub(7L, DashboardService.STATUS_PUBLISHED);
        when(dashboardMapper.findByCodeAndTenant("SALES_OVERVIEW", TID)).thenReturn(existing);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(req()));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("published"));
        verify(dashboardMapper, never()).updateById(any());
    }

    @Test
    void save_archived_throws409() {
        BiDashboard existing = stub(7L, DashboardService.STATUS_ARCHIVED);
        when(dashboardMapper.findByCodeAndTenant("SALES_OVERVIEW", TID)).thenReturn(existing);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(req()));
        assertEquals(409, ex.getCode());
    }

    @Test
    void save_draft_updatesLayout() {
        BiDashboard existing = stub(7L, DashboardService.STATUS_DRAFT);
        when(dashboardMapper.findByCodeAndTenant("SALES_OVERVIEW", TID)).thenReturn(existing);
        SaveDashboardRequest r = req();
        r.setLayout(java.util.Map.of("theme", "dark"));
        BiDashboard out = service.save(r);
        assertEquals("draft", out.getStatus());
        verify(dashboardMapper).updateById(existing);
    }

    // ---------- publish / archive ----------

    @Test
    void publish_draftToPublished_setsPublishedAt() {
        BiDashboard d = stub(7L, DashboardService.STATUS_DRAFT);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        BiDashboard out = service.publish(7L);
        assertEquals("published", out.getStatus());
        assertNotNull(out.getPublishedAt());
    }

    @Test
    void publish_alreadyPublished_throws409() {
        BiDashboard d = stub(7L, DashboardService.STATUS_PUBLISHED);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.publish(7L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void publish_archived_throws409() {
        BiDashboard d = stub(7L, DashboardService.STATUS_ARCHIVED);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.publish(7L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void archive_published_succeeds() {
        BiDashboard d = stub(7L, DashboardService.STATUS_PUBLISHED);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        BiDashboard out = service.archive(7L);
        assertEquals("archived", out.getStatus());
    }

    // ---------- get: 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        BiDashboard other = stub(7L, DashboardService.STATUS_DRAFT);
        other.setTenantId(999L);
        when(dashboardMapper.selectById(7L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(7L));
        assertEquals(404, ex.getCode());
    }

    // ---------- getFullDashboard: checkAccess ----------

    @Test
    void getFullDashboard_noAccess_throws403() {
        BiDashboard d = stub(7L, DashboardService.STATUS_DRAFT);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        when(permissionService.currentPrincipal()).thenReturn(
            new PermissionService.Principal(UID, Set.of("user"), null));
        when(permissionService.checkAccess(eq("dashboard"), eq(7L), eq("user"), eq(UID)))
            .thenReturn(false);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getFullDashboard(7L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("No access"));
    }

    @Test
    void getFullDashboard_hasAccess_returnsFull() {
        BiDashboard d = stub(7L, DashboardService.STATUS_PUBLISHED);
        when(dashboardMapper.selectById(7L)).thenReturn(d);
        when(permissionService.currentPrincipal()).thenReturn(
            new PermissionService.Principal(UID, Set.of("user"), null));
        when(permissionService.checkAccess(eq("dashboard"), eq(7L), eq("user"), eq(UID)))
            .thenReturn(true);
        when(widgetMapper.findByDashboard(7L)).thenReturn(List.of());
        java.util.Map<String, Object> out = service.getFullDashboard(7L);
        assertEquals(d, out.get("dashboard"));
        assertTrue(out.containsKey("widgets"));
        assertTrue(out.containsKey("datasets"));
    }
}