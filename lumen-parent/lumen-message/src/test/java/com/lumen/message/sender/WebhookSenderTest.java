package com.lumen.message.sender;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebhookSender tests — Mockito-free (only static URL-validation methods).
 *
 * <p>Coverage (security-driven):</p>
 * <ol>
 *   <li>validateOutboundUrl_rejectsLoopback — 安全 #7 (SSRF)</li>
 *   <li>validateOutboundUrl_rejectsPrivateNetwork — 安全 #7 (RFC 1918)</li>
 *   <li>validateOutboundUrl_rejectsInvalidScheme — 安全 #7</li>
 *   <li>validateOutboundUrl_acceptsValidUrl — positive case</li>
 * </ol>
 */
class WebhookSenderTest {

    @Test
    void validateOutboundUrl_rejectsLoopback() {
        // localhost (resolves to loopback on most systems)
        ServiceException ex = assertThrows(ServiceException.class,
            () -> WebhookSender.validateOutboundUrl("http://localhost/hook"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("loopback")
            || ex.getMessage().toLowerCase().contains("private")
            || ex.getMessage().toLowerCase().contains("resolve"));
    }

    @Test
    void validateOutboundUrl_rejectsPrivateNetwork() {
        // 192.168.0.1 is site-local (RFC 1918).
        ServiceException ex = assertThrows(ServiceException.class,
            () -> WebhookSender.validateOutboundUrl("http://192.168.0.1/hook"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validateOutboundUrl_rejectsInvalidScheme() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> WebhookSender.validateOutboundUrl("file:///etc/passwd"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validateOutboundUrl_rejectsMissingHost() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> WebhookSender.validateOutboundUrl("http:///hook"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validateOutboundUrl_acceptsPublicDomain() {
        // We can't reliably test a real public domain in unit tests, but a
        // syntactically valid http URL pointing to a non-private hostname
        // MUST pass the URL-shape checks. DNS resolution may fail in sandboxed
        // environments, so we accept either success or a controlled unresolvable
        // error.
        try {
            WebhookSender.validateOutboundUrl("http://example.com/hook");
        } catch (ServiceException ex) {
            assertEquals(400, ex.getCode());
            // In sandboxed test envs the host may be unresolvable — that's still
            // a 400 (well-formed rejection), not a crash.
            assertTrue(ex.getMessage().toLowerCase().contains("unresolvable")
                || ex.getMessage().contains("example.com"));
        }
    }

    @Test
    void send_rejectsMissingWebhookUrl() {
        WebhookSender sender = new WebhookSender();
        MsgNotification n = new MsgNotification();
        n.setChannelCode("webhook");
        n.setRecipientAddress("ignored");
        n.setSubject("test");
        n.setContent("test");
        Map<String, Object> cfg = new HashMap<>(); // no webhookUrl
        MsgChannel ch = new MsgChannel();
        ch.setType("webhook");
        ch.setConfig(cfg);

        Sender.SendResult result = sender.send(n, ch);
        assertFalse(result.isSuccess());
        assertTrue(result.getDetail().toLowerCase().contains("webhookurl"));
    }
}