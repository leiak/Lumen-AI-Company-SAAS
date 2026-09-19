package com.lumen.bi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 授权请求。安全要求 #7: grant/revoke 仅 admin。
 */
@Data
public class GrantPermissionRequest {

    @NotBlank(message = "resourceType is required")
    @Pattern(regexp = "^(dashboard|metric|report|dataset)$",
        message = "resourceType must be one of: dashboard/metric/report/dataset")
    private String resourceType;

    @NotBlank(message = "resourceId is required")
    private String resourceId;

    @NotBlank(message = "principalType is required")
    @Pattern(regexp = "^(user|role|dept)$",
        message = "principalType must be one of: user/role/dept")
    private String principalType;

    @NotBlank(message = "principalId is required")
    private String principalId;

    @NotBlank(message = "permission is required")
    @Pattern(regexp = "^(view|edit|admin)$",
        message = "permission must be one of: view/edit/admin")
    private String permission;
}