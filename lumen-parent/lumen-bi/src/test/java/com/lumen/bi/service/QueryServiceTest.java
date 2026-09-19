package com.lumen.bi.service;

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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * QueryService 测试。安全要求 #3-#5: SQL 白名单校验 + LIMIT 强制 + tenant 注入。
 */
@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock private JdbcTemplate biJdbc;
    @InjectMocks private QueryService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(java.util.Set.of("bi_admin")).build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    // ---------- executeAdHoc: 安全要求 ----------

    @Test
    void executeAdHoc_selectOnly_succeeds() {
        when(biJdbc.queryForList(any(String.class), any(Object[].class)))
            .thenReturn(List.of(Map.of("id", 1, "name", "x")));
        Map<String, Object> result = service.executeAdHoc(
            "SELECT id, name FROM bi_metric WHERE id > :id",
            Map.of("id", 5));
        assertNotNull(result.get("rows"));
        assertEquals(1, result.get("rowCount"));
        // limit 强制 1000
        assertEquals(1000, result.get("limit"));
    }

    @Test
    void executeAdHoc_insert_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("INSERT INTO evil VALUES (1)", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void executeAdHoc_update_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("UPDATE evil SET x = 1", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void executeAdHoc_delete_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("DELETE FROM bi_metric", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void executeAdHoc_drop_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("DROP TABLE bi_metric", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void executeAdHoc_multiStatement_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("SELECT 1; DROP TABLE bi_metric", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void executeAdHoc_empty_throws400() {
        assertEquals(400, assertThrows(ServiceException.class,
            () -> service.executeAdHoc("", null)).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> service.executeAdHoc(null, null)).getCode());
    }

    @Test
    void executeAdHoc_missingParam_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("SELECT * FROM x WHERE id = :pid", new HashMap<>()));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("missing param"));
    }

    @Test
    void executeAdHoc_tenantFilterInjected() {
        when(biJdbc.queryForList(any(String.class), any(Object[].class)))
            .thenReturn(List.of());
        service.executeAdHoc("SELECT * FROM bi_metric WHERE id = :pid",
            Map.of("pid", 1));
        // 验证 SQL 含 tenant_id 占位 + LIMIT
        org.mockito.ArgumentCaptor<String> sqlCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> paramCap = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(biJdbc).queryForList(sqlCap.capture(), paramCap.capture());
        String sql = sqlCap.getValue();
        assertTrue(sql.toLowerCase().contains("tenant_id = ?"));
        assertTrue(sql.toUpperCase().contains("LIMIT 1000"));
        // 参数: pid=1, tenant_id=1
        Object[] params = paramCap.getValue();
        assertEquals(2, params.length);
        assertEquals(1, params[0]);
        assertEquals(TID, params[1]);
    }

    @Test
    void executeAdHoc_noUserContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () ->
            service.executeAdHoc("SELECT 1", null));
        assertEquals(401, ex.getCode());
    }

    @Test
    void executeAdHoc_emptyRows_returnsEmptyColumns() {
        when(biJdbc.queryForList(any(String.class), any(Object[].class)))
            .thenReturn(List.of());
        Map<String, Object> result = service.executeAdHoc("SELECT id FROM bi_metric", null);
        assertEquals(0, result.get("rowCount"));
        assertEquals(List.of(), result.get("columns"));
    }
}