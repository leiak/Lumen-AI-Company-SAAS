package com.lumen.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.SaveEmployeeSalaryRequest;
import com.lumen.payroll.entity.PayEmployeeSalary;
import com.lumen.payroll.mapper.PayEmployeeSalaryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeSalaryServiceTest {

    @Mock private PayEmployeeSalaryMapper mapper;

    @InjectMocks private EmployeeSalaryService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private SaveEmployeeSalaryRequest req() {
        SaveEmployeeSalaryRequest r = new SaveEmployeeSalaryRequest();
        r.setEmployeeId(200L);
        r.setStructureId(11L);
        r.setBaseSalary(new BigDecimal("10000.00"));
        r.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return r;
    }

    @Test
    void save_effectiveToBeforeFrom_throws400() {
        SaveEmployeeSalaryRequest r = req();
        r.setEffectiveTo(LocalDate.of(2025, 12, 31));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("effectiveTo"));
    }

    @Test
    void save_effectiveToEqualsFrom_throws400() {
        SaveEmployeeSalaryRequest r = req();
        r.setEffectiveTo(LocalDate.of(2026, 1, 1));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_conflictByUniqueEmployeeAndEffectiveFrom_throws409() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(req()));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("effectiveFrom"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void save_normal_insertsWithTenant() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.insert(any(PayEmployeeSalary.class))).thenAnswer(inv -> {
            PayEmployeeSalary s = inv.getArgument(0);
            s.setId(40L);
            return 1;
        });
        PayEmployeeSalary saved = service.save(req());
        assertEquals(40L, saved.getId());
        assertEquals(TID, saved.getTenantId());
        assertEquals("active", saved.getStatus());
    }

    @Test
    void save_noTenant_throws401() {
        UserContextHolder.clear();
        UserContextHolder.set(UserContext.builder().userId(UID).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(req()));
        assertEquals(401, ex.getCode());
    }

    @Test
    void get_crossTenant_returns404() {
        PayEmployeeSalary other = new PayEmployeeSalary();
        other.setId(11L);
        other.setTenantId(999L);
        when(mapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void listByEmployee_filtersCrossTenant() {
        PayEmployeeSalary s1 = new PayEmployeeSalary();
        s1.setId(1L); s1.setTenantId(TID); s1.setEmployeeId(200L);
        PayEmployeeSalary s2 = new PayEmployeeSalary();
        s2.setId(2L); s2.setTenantId(999L); s2.setEmployeeId(200L);
        when(mapper.findByEmployee(200L)).thenReturn(List.of(s1, s2));
        List<PayEmployeeSalary> out = service.listByEmployee(200L);
        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).getId());
    }

    @Test
    void findEffective_missing_throws404() {
        when(mapper.findByEmployeeAndEffectiveAt(any(), any())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.findEffective(200L, LocalDate.of(2026, 9, 1), TID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void findEffective_crossTenant_throws404() {
        PayEmployeeSalary other = new PayEmployeeSalary();
        other.setId(11L);
        other.setTenantId(999L);
        other.setEmployeeId(200L);
        when(mapper.findByEmployeeAndEffectiveAt(any(), any())).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.findEffective(200L, LocalDate.of(2026, 9, 1), TID));
        assertEquals(404, ex.getCode());
    }
}