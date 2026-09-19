package com.lumen.hr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubmitPerformanceRequest {

    @NotNull(message = "cycleId is required")
    private Long cycleId;

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "score is required")
    @Min(value = 0, message = "score must be >= 0")
    @Max(value = 100, message = "score must be <= 100")
    private BigDecimal score;

    private String comment;
}
