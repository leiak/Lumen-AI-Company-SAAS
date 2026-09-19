package com.lumen.message.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.message.entity.MsgNotification;
import com.lumen.message.entity.MsgTemplate;
import com.lumen.message.mapper.MsgNotificationMapper;
import com.lumen.message.sender.SiteSender;
import com.lumen.message.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NotificationService tests — Mockito only, no Spring / WebSocket / MySQL.
 *
 * <p>Coverage (security-driven):</p>
 * <ol>
 *   <li>send_site_writesNotificationAndStatusSent — happy path</li>
 *   <li>send_email_disabled_fallsBackToSite — disabled email → site fallback</li>
 *   <li>send_missingTemplate_throws404</li>
 *   <li>send_missingVariable_throws400</li>
 *   <li>markRead_otherUser_throws403 — non-owner, non-super_admin → 403</li>
 *   <li>markReadBatch_marksAllOwned — atomic SQL filter</li>
 *   <li>listByUser_filtersByTenantAndRecipient — pinned userId from context</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private MsgNotificationMapper notificationMapper;
    @Mock private NotificationWebSocketHandler webSocketHandler;
    @Mock private ChannelService channelService;
    @Mock private TemplateService templateService;

    private NotificationService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        SiteSender siteSender = new SiteSender();
        service = new NotificationService(
            notificationMapper, channelService, templateService, webSocketHandler,
            List.of(siteSender));
        // Disable sleep-driven backoff for fast tests.
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "backoffMillis", new long[]{0L, 0L});

        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(new HashSet<>(Set.of("user")))
            .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // send: happy path
    // ---------------------------------------------------------------

    @Test
    void send_site_writesNotificationAndStatusSent() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setSubject("Hi {{name}}");
        tpl.setContent("Welcome {{name}}, code={{code}}");
        tpl.setVariables(List.of("name", "code"));
        tpl.setEnabled(1);
        when(templateService.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);
        when(templateService.render(eq("welcome"), eq("site"), any(), eq(TID)))
            .thenReturn(new TemplateService.Rendered("welcome", "site", "Hi alice",
                "Welcome alice, code=ABC"));

        when(notificationMapper.insert(any(MsgNotification.class))).thenAnswer(inv -> {
            MsgNotification n = inv.getArgument(0);
            n.setId(42L);
            return 1;
        });
        when(notificationMapper.updateById(any(MsgNotification.class))).thenReturn(1);
        when(notificationMapper.selectById(42L)).thenAnswer(inv -> {
            MsgNotification n = new MsgNotification();
            n.setId(42L);
            n.setRecipientUserId(UID);
            n.setStatus(NotificationService.STATUS_SENT);
            return n;
        });

        List<MsgNotification> out = service.send("welcome",
            List.of(UID), Map.of("name", "alice", "code", "ABC"));

        assertEquals(1, out.size());
        assertEquals(42L, out.get(0).getId());
        verify(notificationMapper).insert(any(MsgNotification.class));
        verify(notificationMapper, atLeastOnce()).updateById(any(MsgNotification.class));
    }

    @Test
    void send_email_disabled_fallsBackToSite() {
        // Only site template exists — service falls back to it.
        MsgTemplate siteTpl = new MsgTemplate();
        siteTpl.setCode("otp");
        siteTpl.setChannelCode("site");
        siteTpl.setSubject("OTP");
        siteTpl.setContent("Code: {{code}}");
        siteTpl.setVariables(List.of("code"));
        siteTpl.setEnabled(1);
        when(templateService.findByCodeAndChannel(eq("otp"), eq("site"), eq(TID)))
            .thenReturn(siteTpl);
        when(templateService.render(eq("otp"), eq("site"), any(), eq(TID)))
            .thenReturn(new TemplateService.Rendered("otp", "site", "OTP", "Code: 123"));

        when(notificationMapper.insert(any(MsgNotification.class))).thenAnswer(inv -> {
            MsgNotification n = inv.getArgument(0);
            n.setId(7L);
            return 1;
        });
        when(notificationMapper.selectById(7L)).thenAnswer(inv -> {
            MsgNotification n = new MsgNotification();
            n.setId(7L);
            n.setStatus(NotificationService.STATUS_SENT);
            return n;
        });

        service.send("otp", List.of(UID), Map.of("code", "123"));
        verify(notificationMapper, atLeastOnce()).updateById(any(MsgNotification.class));
    }

    @Test
    void send_missingTemplate_throws404() {
        when(templateService.findByCodeAndChannel(any(), any(), any())).thenReturn(null);
        when(channelService.findEnabledByCodeAndTenant(any(), any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.send("nope", List.of(UID), Map.of()));
        assertEquals(404, ex.getCode());
        verify(notificationMapper, never()).insert(any(MsgNotification.class));
    }

    @Test
    void send_missingVariable_throws400() {
        MsgTemplate tpl = new MsgTemplate();
        tpl.setCode("welcome");
        tpl.setChannelCode("site");
        tpl.setContent("Hi {{name}}");
        tpl.setVariables(List.of("name"));
        tpl.setEnabled(1);
        when(templateService.findByCodeAndChannel(eq("welcome"), eq("site"), eq(TID)))
            .thenReturn(tpl);
        // render() throws 400 for missing variable.
        when(templateService.render(eq("welcome"), eq("site"), any(), eq(TID)))
            .thenThrow(new ServiceException(400, "Missing required template variable: name"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.send("welcome", List.of(UID), Map.of()));
        assertEquals(400, ex.getCode());
        verify(notificationMapper, never()).insert(any(MsgNotification.class));
    }

    // ---------------------------------------------------------------
    // markRead: ownership
    // ---------------------------------------------------------------

    @Test
    void markRead_otherUser_throws403() {
        MsgNotification n = new MsgNotification();
        n.setId(10L);
        n.setRecipientUserId(999L);  // not UID
        n.setTenantId(TID);
        n.setStatus(NotificationService.STATUS_SENT);
        when(notificationMapper.selectById(10L)).thenReturn(n);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.markRead(10L));
        assertEquals(403, ex.getCode());
        verify(notificationMapper, never()).updateById(any(MsgNotification.class));
    }

    // ---------------------------------------------------------------
    // markReadBatch: atomic SQL
    // ---------------------------------------------------------------

    @Test
    void markReadBatch_marksAllOwned() {
        when(notificationMapper.markReadBatch(eq(TID), eq(UID), eq(List.of(1L, 2L, 3L))))
            .thenReturn(3);

        int updated = service.markReadBatch(List.of(1L, 2L, 3L));
        assertEquals(3, updated);
        verify(notificationMapper).markReadBatch(TID, UID, List.of(1L, 2L, 3L));
    }

    // ---------------------------------------------------------------
    // listByUser: tenant + recipient filter
    // ---------------------------------------------------------------

    @Test
    void listByUser_filtersByTenantAndRecipient() {
        when(notificationMapper.listByRecipient(any(Page.class), eq(TID), eq(UID), eq(null)))
            .thenReturn(new Page<MsgNotification>(1, 10));

        IPage<MsgNotification> page = service.listByUser(UID, null, 1, 10);
        assertNotNull(page);
        verify(notificationMapper).listByRecipient(any(Page.class), eq(TID), eq(UID), eq(null));
    }

    @Test
    void listByUser_otherUserId_throws404() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.listByUser(999L, null, 1, 10));
        // Non-owner, non-super_admin → 404 (existence hidden)
        assertEquals(404, ex.getCode());
        verify(notificationMapper, never()).listByRecipient(any(), any(), any(), any());
    }
}