package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.SaveStructureRequest;
import com.lumen.payroll.entity.PaySalaryStructure;
import com.lumen.payroll.mapper.PaySalaryStructureMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryStructureServiceTest {

    @Mock private PaySalaryStructureMapper mapper;

    @InjectMocks private SalaryStructureService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private SaveStructureRequest req() {
        SaveStructureRequest r = new SaveStructureRequest();
        r.setCode("STR-A");
        r.setName("结构A");
        Map<String, Object> c = new HashMap<>();
        c.put("baseSalary", 10000);
        c.put("basicAllowance", 500);
        c.put("positionAllowance", 2000);
        r.setComponents(c);
        return r;
    }

    @Test
    void save_componentsMissingBaseSalary_throws400() {
        SaveStructureRequest r = req();
        r.getComponents().remove("baseSalary");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("baseSalary"));
    }

    @Test
    void save_componentsNull_throws400() {
        SaveStructureRequest r = req();
        r.setComponents(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_componentsBaseSalaryNonPositive_throws400() {
        SaveStructureRequest r = req();
        r.getComponents().put("baseSalary", 0);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_newStructure_insertsWithTenant() {
        when(mapper.findByCodeAndTenant("STR-A", TID)).thenReturn(null);
        when(mapper.insert(any(PaySalaryStructure.class))).thenAnswer(inv -> {
            PaySalaryStructure s = inv.getArgument(0);
            s.setId(11L);
            return 1;
        });
        PaySalaryStructure saved = service.save(req());
        assertEquals(11L, saved.getId());
        assertEquals(TID, saved.getTenantId());
        assertEquals("active", saved.getStatus());
        assertNotNull(saved.getComponents());
        assertEquals(10000, ((Number) saved.getComponents().get("baseSalary")).intValue());
    }

    @Test
    void save_existingStructure_updates() {
        PaySalaryStructure existing = new PaySalaryStructure();
        existing.setId(11L);
        existing.setTenantId(TID);
        existing.setCode("STR-A");
        existing.setName("旧名");
        when(mapper.findByCodeAndTenant("STR-A", TID)).thenReturn(existing);
        PaySalaryStructure out = service.save(req());
        assertEquals(11L, out.getId());
        assertEquals("结构A", out.getName());
        verify(mapper, never()).insert(any());
        verify(mapper).updateById(existing);
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
        PaySalaryStructure other = new PaySalaryStructure();
        other.setId(11L);
        other.setTenantId(999L);
        when(mapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void get_missing_returns404() {
        when(mapper.selectById(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(99L));
        assertEquals(404, ex.getCode());
    }
}