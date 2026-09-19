package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * App 升级检测请求（公开接口）。
 * platform 必须 android/ios/harmony 之一；currentVersion 必须语义版本号。
 */
@Data
public class CheckUpdateRequest {

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(android|ios|harmony)$",
        message = "platform must be one of: android / ios / harmony")
    private String platform;

    @NotBlank(message = "currentVersion is required")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?$",
        message = "currentVersion must be semantic version (e.g. 1.0.0 or 1.0.0-beta)")
    private String currentVersion;
}
