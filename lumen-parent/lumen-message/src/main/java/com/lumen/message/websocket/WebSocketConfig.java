package com.lumen.message.websocket;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket endpoint configuration.
 *
 * <p>Endpoint: {@code /ws/message}. Authentication: the JWT is passed as
 * {@code ?token=...} in the upgrade URL. The {@link JwtHandshakeInterceptor}
 * parses it via {@link JwtTokenProvider} and rejects (401 close) if missing
 * or invalid. The resolved {@code userId} is stored in session attributes so
 * {@link NotificationWebSocketHandler} can key outbound push by user.</p>
 *
 * <p>Hardening applied:</p>
 * <ul>
 *   <li>{@code setMaxTextMessageBufferSize(8192)} — DoS defense. 安全 #11.</li>
 *   <li>{@code setMaxBinaryMessageBufferSize(8192)} — DoS defense. 安全 #11.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** Session attribute key set by {@link JwtHandshakeInterceptor}. */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_TENANT_ID = "tenantId";

    /** Per-session frame cap (8 KB). */
    private static final int MAX_FRAME_SIZE = 8192;

    private final NotificationWebSocketHandler handler;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/message")
            .addInterceptors(new JwtHandshakeInterceptor())
            .setAllowedOriginPatterns("*");
    }

    /**
     * Hard cap on inbound frame sizes — DoS defense (安全 #11).
     *
     * <p>For raw (non-STOMP) WebSocket, {@code WebSocketConfigurer} doesn't expose
     * a transport registration hook — so we register a
     * {@link ServletServerContainerFactoryBean} directly to set the buffer sizes.</p>
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_FRAME_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_FRAME_SIZE);
        container.setMaxSessionIdleTimeout(60_000L); // 60s
        container.setAsyncSendTimeout(20_000L);       // 20s
        return container;
    }

    /**
     * Validate the JWT in the {@code ?token=...} query param during the
     * WebSocket upgrade handshake. Reject (return false) if missing/invalid —
     * Spring will close the upgrade with 401.
     */
    public class JwtHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            String token = extractToken(request);
            if (token == null || token.isBlank()) {
                log.warn("WebSocket handshake rejected: missing token");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                UserContext ctx = jwtTokenProvider.extractUserContext(token);
                if (ctx.getUserId() == null) {
                    response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return false;
                }
                attributes.put(ATTR_USER_ID, ctx.getUserId());
                attributes.put(ATTR_TENANT_ID, ctx.getTenantId());
                return true;
            } catch (JwtException | ServiceException ex) {
                log.warn("WebSocket handshake rejected: invalid token ({})", ex.getMessage());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Exception exception) {
            // no-op
        }

        private String extractToken(ServerHttpRequest request) {
            URI uri = request.getURI();
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) return null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                if ("token".equals(key)) {
                    return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            // Also accept Authorization: Bearer header (some clients prefer that)
            if (request instanceof ServletServerHttpRequest servlet) {
                String auth = servlet.getServletRequest().getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    return auth.substring("Bearer ".length()).trim();
                }
            }
            return null;
        }
    }
}