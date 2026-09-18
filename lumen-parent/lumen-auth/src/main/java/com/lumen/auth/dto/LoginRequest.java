package com.lumen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginRequest {
    @NotNull
    private Long tenantId;
    @NotBlank
    private String userName;
    @NotBlank
    private String password;
    private String captchaId;
    private String captchaCode;
    private String device = "WEB";
}