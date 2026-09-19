package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.ClauseTemplate;
import com.lumen.contract.mapper.ClauseTemplateMapper;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TemplateService tests — renderVariables validation chain.
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private ClauseTemplateMapper templateMapper;

    @InjectMocks private TemplateService templateService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private ClauseTemplate stubTemplate(Long id, List<String> variables) {
        ClauseTemplate t = new ClauseTemplate();
        t.setId(id);
        t.setName("Sales Template");
        t.setType("sales");
        t.setVariables(variables);
        t.setStatus(1);
        t.setTenantId(TENANT);
        return t;
    }

    @Test
    void renderVariables_allDeclaredVarsPresent_returnsRendered() {
        when(templateMapper.selectById(7L))
            .thenReturn(stubTemplate(7L, List.of("name", "amount")));
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        vars.put("amount", 100);

        TemplateService.Rendered r = templateService.renderVariables(7L, vars);
        assertEquals(7L, r.templateId());
        assertEquals(2, r.declared().size());
    }

    @Test
    void renderVariables_missingVar_throws400() {
        when(templateMapper.selectById(7L))
            .thenReturn(stubTemplate(7L, List.of("name", "amount")));
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        // amount missing

        ServiceException ex = assertThrows(ServiceException.class,
            () -> templateService.renderVariables(7L, vars));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("amount"));
    }

    @Test
    void renderVariables_nullVar_throws400() {
        when(templateMapper.selectById(7L))
            .thenReturn(stubTemplate(7L, List.of("name")));
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> templateService.renderVariables(7L, vars));
        assertEquals(400, ex.getCode());
    }

    @Test
    void renderVariables_disabled_throws409() {
        ClauseTemplate t = stubTemplate(7L, List.of("name"));
        t.setStatus(0);
        when(templateMapper.selectById(7L)).thenReturn(t);

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> templateService.renderVariables(7L, vars));
        assertEquals(409, ex.getCode());
    }

    @Test
    void renderVariables_emptyVariables_ok() {
        // No declared variables → caller may pass empty map.
        when(templateMapper.selectById(7L))
            .thenReturn(stubTemplate(7L, List.of()));
        TemplateService.Rendered r = templateService.renderVariables(7L, new HashMap<>());
        assertEquals(0, r.declared().size());
    }
}
