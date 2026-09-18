package com.lumen.common.security.jwt;

import com.lumen.common.security.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final SecretKey signingKey;

    @Autowired
    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "lumen.security.jwt.secret must be at least 32 bytes for HS256, got "
                    + keyBytes.length + " bytes");
        }
        if (props.getSecret().startsWith("lumen-default-")) {
            throw new IllegalStateException(
                "lumen.security.jwt.secret is using the default value. "
                    + "Set a unique secret in production via application.yml or Nacos.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserContext ctx) {
        return generateToken(ctx, "access", props.getAccessExpireSeconds());
    }

    public String generateRefreshToken(UserContext ctx) {
        return generateToken(ctx, "refresh", props.getRefreshExpireSeconds());
    }

    private String generateToken(UserContext ctx, String type, long expireSeconds) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", type);
        claims.put("uid", ctx.getUserId());
        claims.put("tid", ctx.getTenantId());
        claims.put("uname", ctx.getUserName());
        claims.put("nname", ctx.getNickName());
        claims.put("did", ctx.getDeptId());
        claims.put("ds", ctx.getDataScope());

        return Jwts.builder()
            .id(jti)
            .subject(String.valueOf(ctx.getUserId()))
            .issuer(props.getIssuer())
            .issuedAt(now)
            .expiration(exp)
            .claims(claims)
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException e) {
            log.warn("JWT 解析失败 [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    public UserContext extractUserContext(String token) {
        Claims c = parseToken(token);
        String type = c.get("type", String.class);
        if (!"access".equals(type)) {
            throw new io.jsonwebtoken.JwtException("Token type is not 'access': " + type);
        }
        UserContext ctx = new UserContext();
        ctx.setUserId(c.get("uid", Long.class));
        ctx.setTenantId(c.get("tid", Long.class));
        ctx.setUserName(c.get("uname", String.class));
        ctx.setNickName(c.get("nname", String.class));
        ctx.setDeptId(c.get("did", Long.class));
        ctx.setDataScope(c.get("ds", Integer.class));
        ctx.setTokenId(c.getId());
        return ctx;
    }

    public boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
}