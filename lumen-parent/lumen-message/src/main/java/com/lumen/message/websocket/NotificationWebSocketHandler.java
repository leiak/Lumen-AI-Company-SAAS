package com.lumen.message.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw WebSocket handler (NOT STOMP). STOMP broker configuration is TODO.
 *
 * <p>Sessions are keyed by {@code userId} from session attributes (populated by
 * {@link WebSocketConfig} during the JWT handshake). When a user has multiple
 * connections (e.g. desktop + mobile), only the most recent is kept — this is
 * intentional to keep the contract simple.</p>
 *
 * <p>Outbound push is driven by
 * {@link com.lumen.message.service.NotificationService} via
 * {@link #sendToUser(Long, Object)} — which MUST be try/caught so a WebSocket
 * failure never fails the notification send.</p>
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /** User-id → active session. One session per user (last-write-wins). */
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = userIdOf(session);
        if (uid == null) {
            // Handshake interceptor should have set this; if not, drop the session.
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) { /* nothing to do */ }
            return;
        }
        WebSocketSession previous = sessions.put(uid, session);
        if (previous != null && previous.isOpen()) {
            try {
                previous.close(CloseStatus.NORMAL.withReason("replaced by new session"));
            } catch (IOException ignored) { /* nothing to do */ }
        }
        log.info("WebSocket connected userId={} sessionId={}", uid, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Lightweight echo / ping handling — full client protocol is TODO.
        Long uid = userIdOf(session);
        String payload = message.getPayload();
        if (payload != null && payload.startsWith("ping")) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException ex) {
                log.warn("WebSocket pong write failed userId={} {}", uid, ex.getMessage());
            }
            return;
        }
        log.debug("WebSocket text userId={} bytes={} (echo)", uid, payload == null ? 0 : payload.length());
        try {
            session.sendMessage(new TextMessage(payload == null ? "" : payload));
        } catch (IOException ex) {
            log.warn("WebSocket echo write failed userId={} {}", uid, ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = userIdOf(session);
        if (uid != null) {
            // Only remove if WE are the active session (don't race a newer one).
            sessions.remove(uid, session);
        }
        log.info("WebSocket closed userId={} status={}", uid, status);
    }

    /**
     * Push a JSON-serializable payload to a user's active session, if any.
     *
     * @return true if a session was found AND accepted the message
     */
    public boolean sendToUser(Long userId, Object payload) {
        if (userId == null) return false;
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) return false;
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (Exception ex) {
            // Caller MUST try/catch — we surface failure to remove stale session.
            sessions.remove(userId, session);
            log.warn("WebSocket push failed userId={} {}", userId, ex.getMessage());
            return false;
        }
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    private static Long userIdOf(WebSocketSession session) {
        Object uid = session.getAttributes().get(WebSocketConfig.ATTR_USER_ID);
        if (uid instanceof Long l) return l;
        if (uid instanceof Number n) return n.longValue();
        return null;
    }
}