package com.lumen.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetCheckRequest {

    @NotBlank(message = "period is required")
    private String period;

    @NotNull(message = "subjectId is required")
    private Long subjectId;

    @NotNull(message = "deptId is required")
    private Long deptId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;
}
