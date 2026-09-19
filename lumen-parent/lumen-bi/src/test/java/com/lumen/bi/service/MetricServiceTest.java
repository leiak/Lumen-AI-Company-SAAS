package com.lumen.bi.service;

import com.lumen.bi.dto.SaveMetricRequest;
import com.lumen.bi.entity.BiMetric;
import com.lumen.bi.mapper.BiMetricMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

    @Mock private BiMetricMapper metricMapper;
    @InjectMocks private MetricService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private SaveMetricRequest validReq() {
        SaveMetricRequest r = new SaveMetricRequest();
        r.setCode("SALES_TOTAL");
        r.setName("销售总额");
        r.setCategory("sales");
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT SUM(amount) FROM sal_order WHERE tenant_id = :tid");
        def.put("dimensions", List.of("dept"));
        def.put("measures", List.of("amount"));
        def.put("filters", List.of());
        def.put("params", List.of("tid"));
        r.setDefinition(def);
        r.setStatus("active");
        return r;
    }

    // ---------- parseDefinition ----------

    @Test
    void parseDefinition_missingDimensions_throws400() {
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT 1");
        def.put("measures", List.of("x"));
        // dimensions 缺失
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.parseDefinition(def));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("dimensions"));
    }

    @Test
    void parseDefinition_missingMeasures_throws400() {
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT 1");
        def.put("dimensions", List.of("dept"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.parseDefinition(def));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("measures"));
    }

    @Test
    void parseDefinition_missingSql_throws400() {
        Map<String, Object> def = new HashMap<>();
        def.put("dimensions", List.of("dept"));
        def.put("measures", List.of("x"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.parseDefinition(def));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("sql"));
    }

    @Test
    void parseDefinition_nullOrEmpty_throws400() {
        assertEquals(400, assertThrows(ServiceException.class,
            () -> service.parseDefinition(null)).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> service.parseDefinition(new HashMap<>())).getCode());
    }

    // ---------- save ----------

    @Test
    void save_newMetric_inserts() {
        when(metricMapper.findByCodeAndTenant("SALES_TOTAL", TID)).thenReturn(null);
        when(metricMapper.insert(any(BiMetric.class))).thenAnswer(inv -> {
            BiMetric m = inv.getArgument(0);
            m.setId(50L);
            return 1;
        });
        BiMetric m = service.save(validReq());
        assertNotNull(m.getId());
        assertEquals(TID, m.getTenantId());
        assertEquals("SALES_TOTAL", m.getCode());
        assertEquals("sales", m.getCategory());
        assertEquals(1, m.getVersion());
    }

    @Test
    void save_existingMetric_incrementsVersion() {
        BiMetric existing = new BiMetric();
        existing.setId(7L);
        existing.setTenantId(TID);
        existing.setCode("SALES_TOTAL");
        existing.setVersion(3);
        when(metricMapper.findByCodeAndTenant("SALES_TOTAL", TID)).thenReturn(existing);
        BiMetric m = service.save(validReq());
        assertEquals(7L, m.getId());
        assertEquals(4, m.getVersion());
    }

    @Test
    void save_invalidSql_throws400() {
        SaveMetricRequest req = validReq();
        Map<String, Object> def = new HashMap<>(req.getDefinition());
        def.put("sql", "DROP TABLE bi_metric");
        req.setDefinition(def);
        assertEquals(400, assertThrows(ServiceException.class,
            () -> service.save(req)).getCode());
        verify(metricMapper, never()).insert(any());
    }

    // ---------- get: 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        BiMetric other = new BiMetric();
        other.setId(11L); other.setTenantId(999L);
        when(metricMapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void get_notFound_returns404() {
        when(metricMapper.selectById(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(99L));
        assertEquals(404, ex.getCode());
    }

    // ---------- render: 参数化 ----------

    @Test
    void render_missingParam_throws400() {
        BiMetric m = new BiMetric();
        m.setId(1L); m.setTenantId(TID); m.setCode("M1"); m.setStatus("active");
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT * FROM x WHERE id = :pid");
        def.put("dimensions", List.of("id"));
        def.put("measures", List.of("count"));
        m.setDefinition(def);
        when(metricMapper.findByCodeAndTenant("M1", TID)).thenReturn(m);
        // 没传 pid
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("M1", new HashMap<>()));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("missing param"));
    }

    @Test
    void render_inactiveMetric_throws409() {
        BiMetric m = new BiMetric();
        m.setId(1L); m.setTenantId(TID); m.setCode("M1"); m.setStatus("inactive");
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT 1");
        def.put("dimensions", List.of("x"));
        def.put("measures", List.of("y"));
        m.setDefinition(def);
        when(metricMapper.findByCodeAndTenant("M1", TID)).thenReturn(m);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("M1", new HashMap<>()));
        assertEquals(409, ex.getCode());
    }

    @Test
    void render_tenantFilterInjected_paramOrdered() {
        BiMetric m = new BiMetric();
        m.setId(1L); m.setTenantId(TID); m.setCode("M1"); m.setStatus("active");
        Map<String, Object> def = new HashMap<>();
        def.put("sql", "SELECT * FROM x WHERE id = :pid AND status = :st");
        def.put("dimensions", List.of("id"));
        def.put("measures", List.of("count"));
        m.setDefinition(def);
        when(metricMapper.findByCodeAndTenant("M1", TID)).thenReturn(m);

        Map<String, Object> params = new HashMap<>();
        params.put("pid", 42);
        params.put("st", "active");
        MetricService.RenderResult r = service.render("M1", params);
        // SQL 末尾包含 tenant_id = ?
        assertTrue(r.sql().toLowerCase().contains("tenant_id = ?"));
        // 参数顺序: pid, st, tenantId
        assertEquals(3, r.params().size());
        assertEquals(42, r.params().get(0));
        assertEquals("active", r.params().get(1));
        assertEquals(TID, r.params().get(2));
    }
}