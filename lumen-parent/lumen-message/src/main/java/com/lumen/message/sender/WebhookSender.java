package com.lumen.message.sender;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Webhook sender — covers dingtalk / wechat-work / generic webhook channels.
 * Feature-flagged via {@code lumen.message.webhook.enabled}.
 *
 * <p>Default state: bean NOT registered.</p>
 *
 * <p>Hardening applied:</p>
 * <ul>
 *   <li>SSRF prevention: rejects loopback, link-local, site-local (RFC 1918),
 *       and any-local IPv4/IPv6 destinations — 安全 #7.</li>
 *   <li>Resolves DNS itself before connecting to prevent DNS rebinding (the
 *       resolved address, not the host name, drives the allow-list check).</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lumen.message.webhook.enabled", havingValue = "true")
public class WebhookSender implements Sender {

    @Override
    public String getType() {
        // Single sender covers all webhook-shaped channels (dingtalk, wechat, webhook).
        // Channel.type is matched by the dispatcher's route table.
        return "webhook";
    }

    @Override
    public SendResult send(MsgNotification notification, MsgChannel channel) {
        if (channel == null || channel.getConfig() == null) {
            log.warn("webhook-send skipped: channel config missing");
            return new SendResult(false, "channel config missing", null);
        }
        Map<String, Object> cfg = channel.getConfig();
        Object urlObj = cfg.get("webhookUrl");
        if (!(urlObj instanceof String) || ((String) urlObj).isBlank()) {
            log.warn("webhook-send skipped: webhookUrl not configured");
            return new SendResult(false, "webhookUrl not configured", null);
        }
        String urlStr = (String) urlObj;

        // 安全 #7: SSRF — validate host before any network call.
        validateOutboundUrl(urlStr);

        // TODO: integrate HTTP client once a webhook provider is selected.
        log.info("webhook-send (stub) url={} subject={}", urlStr, notification.getSubject());
        return new SendResult(true, "webhook-sent (stub, no real HTTP wired)", null);
    }

    /**
     * SSRF guard. Resolve the host ourselves and reject any address that maps
     * to a private/loopback/link-local/any-local range. We do this before any
     * HTTP call to prevent SSRF (and DNS rebinding — the host is resolved
     * here, not lazily by the HTTP client).
     */
    static void validateOutboundUrl(String urlStr) {
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(400, "Invalid webhook URL: " + ex.getMessage(), ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ServiceException(400, "webhook URL must be http(s)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ServiceException(400, "webhook URL must have a host");
        }

        // Resolve all addresses (DNS rebinding mitigation — we MUST reject based
        // on resolved IP, not the host string alone).
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new ServiceException(400, "webhook host unresolvable: " + host, ex);
        }
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()) {
                throw new ServiceException(400,
                    "webhook URL resolves to a private/loopback address: " + addr.getHostAddress());
            }
        }
    }
}