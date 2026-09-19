package com.lumen.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MilestoneRequest {
    @NotBlank(message = "milestoneName is required")
    private String milestoneName;

    @NotNull(message = "plannedDate is required")
    private LocalDate plannedDate;

    private String note;
}
