package com.lumen.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private long expiresInSeconds;
    private String sessionId;
    private Long userId;
    private Long tenantId;
    private String userName;
    private String nickName;
}