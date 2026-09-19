package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 注册推送 Token 请求。
 * deviceInfo 含 deviceModel / osVersion / appVersion（可选）。
 */
@Data
public class RegisterPushTokenRequest {

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(android|ios|harmony)$",
        message = "platform must be one of: android / ios / harmony")
    private String platform;

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    /** 可选：App 版本。 */
    private String appVersion;

    /** 可选：设备型号。 */
    private String deviceModel;

    /** 可选：操作系统版本。 */
    private String osVersion;
}
