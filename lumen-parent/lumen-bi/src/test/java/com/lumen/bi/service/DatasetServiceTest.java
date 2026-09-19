package com.lumen.bi.service;

import com.lumen.bi.entity.BiDataset;
import com.lumen.bi.mapper.BiDatasetMapper;
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

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock private BiDatasetMapper datasetMapper;
    @Mock private JdbcTemplate biJdbc;
    @InjectMocks private DatasetService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private BiDataset stub(long id, String code) {
        BiDataset d = new BiDataset();
        d.setId(id);
        d.setTenantId(TID);
        d.setCode(code);
        d.setName("dataset");
        d.setStatus("active");
        Map<String, Object> model = new HashMap<>();
        model.put("sql", "SELECT * FROM fin_voucher");
        d.setModel(model);
        return d;
    }

    // ---------- preview: pageSize 1..200 ----------

    @Test
    void preview_pageSizeZero_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("DS1", 1, 0));
        assertEquals(400, ex.getCode());
    }

    @Test
    void preview_pageSizeOver200_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("DS1", 1, 201));
        assertEquals(400, ex.getCode());
    }

    @Test
    void preview_pageZero_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("DS1", 0, 100));
        assertEquals(400, ex.getCode());
    }

    @Test
    void preview_datasetNotFound_throws404() {
        when(datasetMapper.findByCodeAndTenant("MISSING", TID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("MISSING", 1, 100));
        assertEquals(404, ex.getCode());
    }

    @Test
    void preview_inactiveDataset_throws409() {
        BiDataset d = stub(1L, "DS1");
        d.setStatus("inactive");
        when(datasetMapper.findByCodeAndTenant("DS1", TID)).thenReturn(d);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("DS1", 1, 100));
        assertEquals(409, ex.getCode());
    }

    @Test
    void preview_validDataset_executesSql() {
        BiDataset d = stub(1L, "DS1");
        when(datasetMapper.findByCodeAndTenant("DS1", TID)).thenReturn(d);
        when(biJdbc.queryForList(any(String.class), eq(TID)))
            .thenReturn(List.of(Map.of("id", 1)));
        Map<String, Object> out = service.preview("DS1", 1, 100);
        assertEquals(1, out.get("page"));
        assertEquals(100, out.get("pageSize"));
        assertNotNull(out.get("rows"));
    }

    @Test
    void preview_sqlWithUpdate_throws400() {
        BiDataset d = stub(1L, "DS1");
        Map<String, Object> model = new HashMap<>();
        model.put("sql", "UPDATE fin_voucher SET amount = 0");
        d.setModel(model);
        when(datasetMapper.findByCodeAndTenant("DS1", TID)).thenReturn(d);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.preview("DS1", 1, 100));
        assertEquals(400, ex.getCode());
    }

    // ---------- get: 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        BiDataset other = stub(11L, "DS1");
        other.setTenantId(999L);
        when(datasetMapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }
}