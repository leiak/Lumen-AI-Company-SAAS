package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrResignationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private HrEmployeeMapper employeeMapper;
    @Mock private HrResignationMapper resignationMapper;

    @InjectMocks private DashboardService dashboardService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).userName("hr")
            .roles(new HashSet<>(Set.of("hr_admin"))).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void stats_returnsAggregatedCounters() {
        when(employeeMapper.selectCount((Wrapper<HrEmployee>) null)).thenReturn(120L);
        when(employeeMapper.selectCount(any(Wrapper.class))).thenReturn(80L, 30L, 10L);
        when(resignationMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        Map<String, Long> out = dashboardService.stats();

        assertEquals(120L, out.get("totalEmployees"));
        assertEquals(80L, out.get("activeEmployees"));
        assertEquals(30L, out.get("probationEmployees"));
        assertEquals(10L, out.get("resignedEmployees"));
        assertEquals(5L, out.get("pendingResignations"));
    }

    @Test
    void stats_missingTenantContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> dashboardService.stats());
        assertEquals(401, ex.getCode());
    }

    @Test
    void stats_nullCountersFallBackToZero() {
        when(employeeMapper.selectCount((Wrapper<HrEmployee>) null)).thenReturn(null);
        when(employeeMapper.selectCount(any(Wrapper.class))).thenReturn(null);
        when(resignationMapper.selectCount(any(Wrapper.class))).thenReturn(null);

        Map<String, Long> out = dashboardService.stats();
        assertEquals(0L, out.get("totalEmployees"));
        assertEquals(0L, out.get("activeEmployees"));
        assertEquals(0L, out.get("probationEmployees"));
        assertEquals(0L, out.get("resignedEmployees"));
        assertEquals(0L, out.get("pendingResignations"));
    }
}
