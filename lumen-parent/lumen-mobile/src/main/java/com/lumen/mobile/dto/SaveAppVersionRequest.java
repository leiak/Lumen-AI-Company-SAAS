package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建/更新 App 版本请求（管理员）。
 */
@Data
public class SaveAppVersionRequest {

    private Long id;

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(android|ios|harmony)$",
        message = "platform must be one of: android / ios / harmony")
    private String platform;

    @NotBlank(message = "version is required")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?$",
        message = "version must be semantic version (e.g. 1.0.0)")
    private String version;

    @NotNull(message = "buildNumber is required")
    private Integer buildNumber;

    /** 0=不强制 1=强制升级；默认 0。 */
    private Integer forceUpdate;

    private String minSupportedVersion;

    @NotBlank(message = "downloadUrl is required")
    private String downloadUrl;

    private String releaseNotes;

    /** draft / released / deprecated；默认 draft。 */
    private String status;

    private LocalDateTime releasedAt;
}
