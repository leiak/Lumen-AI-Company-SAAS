package com.lumen.hr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SubmitResignationRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    private String reason;

    @NotNull(message = "effectiveAt is required")
    private LocalDate effectiveAt;
}
