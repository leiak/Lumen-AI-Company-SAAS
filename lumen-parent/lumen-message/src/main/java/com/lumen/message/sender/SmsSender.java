package com.lumen.message.sender;

import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SMS sender — aliyun/tencent cloud adapter. Feature-flagged via
 * {@code lumen.message.sms.enabled}.
 *
 * <p>Default state: bean NOT registered. When enabled but gateway credentials are
 * absent, this sender returns a failure result so {@code NotificationService}
 * marks the row status=2 (failed) after the retry ceiling.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lumen.message.sms.enabled", havingValue = "true")
public class SmsSender implements Sender {

    @Override
    public String getType() {
        return "sms";
    }

    @Override
    public SendResult send(MsgNotification notification, MsgChannel channel) {
        if (channel == null || channel.getConfig() == null
            || channel.getConfig().get("accessKey") == null) {
            log.warn("sms-send skipped: SMS gateway not configured");
            return new SendResult(false, "SMS gateway not configured", null);
        }
        // TODO: integrate aliyun/tencent SMS SDK once credentials are provisioned.
        log.info("sms-send (stub) to={} templateCode={}",
            notification.getRecipientAddress(), notification.getTemplateCode());
        return new SendResult(false, "SMS gateway not configured", null);
    }
}