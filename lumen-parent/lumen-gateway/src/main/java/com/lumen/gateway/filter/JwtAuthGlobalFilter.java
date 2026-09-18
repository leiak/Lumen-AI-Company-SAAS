package com.lumen.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumen.common.core.domain.R;
import com.lumen.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
        "/auth/login",
        "/auth/refresh",
        "/auth/health",
        "/mfa/verify",
        "/platform/health",
        "/system/health",
        "/org/health"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }
        String token = auth.substring(7);

        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(token);
        } catch (Exception e) {
            log.warn("JWT parse failed for path={}: {}", path, e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            return unauthorized(exchange, "Token type must be 'access'");
        }
        Long uid = claims.get("uid", Long.class);
        Long tid = claims.get("tid", Long.class);
        if (uid == null || tid == null) {
            return unauthorized(exchange, "Invalid token claims");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header("X-User-Id", String.valueOf(uid))
            .header("X-Tenant-Id", String.valueOf(tid))
            .header("X-User-Name", String.valueOf(claims.get("uname", String.class)))
            .header("X-Session-Id", claims.getId())
            .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(R.fail(401, msg));
            DataBuffer buf = resp.bufferFactory().wrap(body);
            return resp.writeWith(Mono.just(buf));
        } catch (Exception e) {
            return resp.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}