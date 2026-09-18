package com.lumen.common.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lumen.security.jwt")
public class JwtProperties {
    private String secret = "lumen-default-secret-key-please-change-in-production-env-32bytes";
    private long accessExpireSeconds = 1800;
    private long refreshExpireSeconds = 604800;
    private String issuer = "lumen";
    private String header = "Authorization";
    private String tokenPrefix = "Bearer ";
}