package com.lumen.hr.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTrainingPlanRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "startAt is required")
    private LocalDateTime startAt;

    @NotNull(message = "endAt is required")
    private LocalDateTime endAt;

    @Min(value = 1, message = "capacity must be >= 1")
    private Integer capacity;
}
