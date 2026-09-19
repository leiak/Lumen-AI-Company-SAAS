package com.lumen.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class StartOnboardingRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    /** Initial checklist; items default to false (pending). */
    @NotBlank(message = "checklist must not be empty")
    private String templateCode;
}
