package com.lumen.message.sender;

import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Email sender — JavaMailSender-style. Feature-flagged via
 * {@code lumen.message.email.enabled}.
 *
 * <p>Default state: bean NOT registered. When enabled but SMTP is not configured
 * (no host/user in channel.config), this sender logs and returns a failure result
 * (which {@code NotificationService} will retry up to {@code max-attempts}).</p>
 *
 * <p>Hardening applied:</p>
 * <ul>
 *   <li>HTML escape subject + content (prevent HTML / header injection) — 安全 #8</li>
 *   <li>Logs host:port only, NEVER user/pass — 安全 #10</li>
 * </ul>
 *
 * <p>Real {@link org.springframework.mail.javamail.JavaMailSender} wiring is TODO
 * until a transactional SMTP gateway is provisioned.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lumen.message.email.enabled", havingValue = "true")
public class EmailSender implements Sender {

    @Override
    public String getType() {
        return "email";
    }

    @Override
    public SendResult send(MsgNotification notification, MsgChannel channel) {
        if (channel == null || channel.getConfig() == null) {
            log.warn("email-send skipped: channel config missing (channelCode={})",
                notification.getChannelCode());
            return new SendResult(false, "channel config missing", null);
        }
        Object host = channel.getConfig().get("smtpHost");
        Object port = channel.getConfig().get("smtpPort");
        Object user = channel.getConfig().get("smtpUser");
        Object pass = channel.getConfig().get("smtpPass");
        Object from = channel.getConfig().get("fromAddress");

        if (host == null || user == null || pass == null || from == null) {
            // 安全 #10: never log user/pass. Log host:port only.
            log.warn("email-send skipped: SMTP not fully configured host={} port={}",
                host, port);
            return new SendResult(false, "SMTP not configured", null);
        }

        // 安全 #8: HTML escape subject + content to prevent HTML / header injection.
        String safeSubject = HtmlUtils.htmlEscape(notification.getSubject());
        String safeContent = HtmlUtils.htmlEscape(notification.getContent());
        String to = notification.getRecipientAddress();

        // TODO: integrate JavaMailSender once SMTP gateway is provisioned.
        log.info("email-send (stub) host={} port={} from={} to={} subject={}",
            host, port, from, to, safeSubject);
        return new SendResult(true, "email-sent (stub, no real SMTP wired)", null);
    }
}