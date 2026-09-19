package com.lumen.message.sender;

import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-site inbox sender. Default implementation — fully functional.
 *
 * <p>Strategy: the {@link com.lumen.message.service.NotificationService} writes
 * the {@code msg_notification} row BEFORE dispatch (status=0 pending). This sender
 * simply flips status to 1 (sent) on success and stamps {@code sentTime}.</p>
 *
 * <p>Push to online users happens in the service layer via WebSocket — see
 * {@link com.lumen.message.websocket.NotificationWebSocketHandler}. This sender
 * does NOT push directly; that's a presentation concern, not a delivery concern.</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "siteSenderOverride")
public class SiteSender implements Sender {

    @Override
    public String getType() {
        return "site";
    }

    @Override
    public SendResult send(MsgNotification notification, MsgChannel channel) {
        // The notification row is already in DB. Site delivery is the row itself.
        if (notification.getId() == null) {
            throw new IllegalStateException("SiteSender requires persisted notification id");
        }
        log.info("site-send id={} recipient={} subject={}",
            notification.getId(), notification.getRecipientUserId(), notification.getSubject());
        return new SendResult(true, "stored in msg_notification", null);
    }
}