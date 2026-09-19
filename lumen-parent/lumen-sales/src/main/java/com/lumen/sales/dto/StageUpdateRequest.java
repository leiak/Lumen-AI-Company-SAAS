package com.lumen.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StageUpdateRequest {
    @NotNull(message = "newStage is required")
    private String newStage;

    @NotNull(message = "expectedCloseDate is required")
    private LocalDate expectedCloseDate;
}