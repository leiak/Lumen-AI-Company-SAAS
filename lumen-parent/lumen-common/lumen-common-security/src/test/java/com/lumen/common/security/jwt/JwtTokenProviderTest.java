package com.lumen.common.security.jwt;

import com.lumen.common.security.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("test-secret-key-32-bytes-min-length-1234567890");
        provider = new JwtTokenProvider(props);
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        UserContext ctx = UserContext.builder()
            .userId(100L)
            .tenantId(1L)
            .userName("admin")
            .nickName("管理员")
            .deptId(10L)
            .dataScope(1)
            .build();

        String token = provider.generateAccessToken(ctx);
        assertNotNull(token);

        Claims claims = provider.parseToken(token);
        assertEquals("100", claims.getSubject());
        assertEquals(100L, claims.get("uid", Long.class));
        assertEquals(1L, claims.get("tid", Long.class));
        assertEquals("admin", claims.get("uname", String.class));
    }

    @Test
    void shouldExtractUserContext() {
        UserContext ctx = UserContext.builder()
            .userId(200L)
            .tenantId(2L)
            .userName("user1")
            .build();

        String token = provider.generateAccessToken(ctx);
        UserContext extracted = provider.extractUserContext(token);

        assertEquals(200L, extracted.getUserId());
        assertEquals(2L, extracted.getTenantId());
        assertEquals("user1", extracted.getUserName());
        assertNotNull(extracted.getTokenId());
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThrows(JwtException.class, () -> provider.parseToken("invalid.token.here"));
    }

    @Test
    void shouldDetectExpired() {
        props.setAccessExpireSeconds(-1);
        UserContext ctx = UserContext.builder().userId(1L).tenantId(1L).build();
        String token = provider.generateAccessToken(ctx);
        assertTrue(provider.isExpired(token));
    }
}