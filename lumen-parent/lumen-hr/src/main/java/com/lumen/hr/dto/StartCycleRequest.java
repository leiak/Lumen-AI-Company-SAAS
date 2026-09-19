package com.lumen.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StartCycleRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "periodStart is required")
    private LocalDate periodStart;

    @NotNull(message = "periodEnd is required")
    private LocalDate periodEnd;
}
