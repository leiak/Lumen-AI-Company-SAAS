package com.lumen.message.sender;

import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pluggable sender interface. Each implementation handles one channel type
 * (site / email / sms / webhook).
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@link #getType()} must match {@link MsgChannel#getType()} (e.g. {@code "site"}).</li>
 *   <li>{@link #send(MsgNotification, MsgChannel)} either:
 *     <ul>
 *       <li>returns a successful {@link SendResult}, OR</li>
 *       <li>throws a {@link RuntimeException} — {@code NotificationService} catches it
 *           and triggers retry.</li>
 *     </ul>
 *   </li>
 *   <li>Implementations MUST be idempotent w.r.t. retries: a notification row
 *       is created BEFORE send, so retries re-write the same row.</li>
 * </ul>
 */
public interface Sender {

    /**
     * The {@link MsgChannel#getType()} value this sender handles.
     */
    String getType();

    /**
     * Send the notification. Implementation MUST NOT swallow exceptions — if
     * the channel refused to deliver, throw so {@code NotificationService} can
     * drive retry/backoff.
     *
     * @param notification row that has already been persisted (status=0 pending).
     * @param channel      channel definition (provides type + config).
     */
    SendResult send(MsgNotification notification, MsgChannel channel);

    /**
     * Outcome of a single send attempt.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class SendResult {
        private boolean success;
        private String detail;       // optional human-readable detail (logged, never to client)
        private String externalId;   // provider's message id (for SMS/email delivery receipts)
    }
}