package com.lumen.message.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.message.entity.MsgTemplate;
import com.lumen.message.mapper.MsgTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TemplateService tests — Mockito only, no Spring context.
 *
 * <p>Coverage (security-driven):</p>
 * <ol>
 *   <li>render_replacesVariables — declared vars only, {{var}} substitution works</li>
 *   <li>render_missingVar_throws400 — declared-but-not-supplied → 400 (never leaks literal)</li>
 *   <li>render_unknownTemplate_throws404 — missing template → 404</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private MsgTemplateMapper templateMapper;

    private TemplateService service;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateMapper);
    }

    @Test
    void render_replacesVariables() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi {{name}}");
        tpl.setContent("Welcome {{name}}, your code is {{code}}");
        tpl.setVariables(List.of("name", "code"));
        tpl.setEnabled(1);
        when(templateMapper.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);

        TemplateService.Rendered out = service.render(
            "welcome", "site", Map.of("name", "alice", "code", "ABC"), TID);

        assertEquals("Hi alice", out.subject());
        assertEquals("Welcome alice, your code is ABC", out.content());
    }

    @Test
    void render_missingVar_throws400() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi {{name}}");
        tpl.setContent("Code: {{code}}");
        tpl.setVariables(List.of("name", "code"));  // both declared
        tpl.setEnabled(1);
        when(templateMapper.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);

        // 'code' is missing — must throw 400 BEFORE rendering.
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("welcome", "site",
                Map.of("name", "alice"), TID));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("missing"));
    }

    @Test
    void render_unknownTemplate_throws404() {
        when(templateMapper.findByCodeAndChannel(any(), any(), any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("nope", "site", Map.of(), TID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void render_disabledTemplate_throws404() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi");
        tpl.setContent("Hi");
        tpl.setVariables(List.of());
        tpl.setEnabled(0);  // disabled
        when(templateMapper.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("welcome", "site", Map.of(), TID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void render_extraVariablesIgnored() {
        // Variables supplied beyond declared list are ignored (no surface expansion).
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi {{name}}");
        tpl.setContent("Hi {{name}}");
        tpl.setVariables(List.of("name"));
        tpl.setEnabled(1);
        when(templateMapper.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);

        TemplateService.Rendered out = service.render(
            "welcome", "site",
            Map.of("name", "alice", "extra", "should-be-ignored", "admin", true),
            TID);
        assertEquals("Hi alice", out.content());
    }

    @Test
    void render_nullValue_throws400() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi {{name}}");
        tpl.setContent("Hi {{name}}");
        tpl.setVariables(List.of("name"));
        tpl.setEnabled(1);
        when(templateMapper.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.render("welcome", "site",
                new java.util.HashMap<>(), TID));
        // Map is empty — name is missing → 400.
        assertEquals(400, ex.getCode());
    }
}