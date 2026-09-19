package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 移动端登录请求（手机号 + 短信验证码）。
 * 短信验证码: TODO P5 接入 SMS 通道。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^1[3-9]\\d{9}$",
        message = "phone must be a valid Chinese mobile number (11 digits)")
    private String phone;

    @NotBlank(message = "smsCode is required")
    @Pattern(regexp = "^\\d{6}$",
        message = "smsCode must be 6 digits")
    private String smsCode;
}
