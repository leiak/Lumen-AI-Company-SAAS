package com.lumen.contract.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentPlanDto {
    private String planNo;

    @Positive(message = "plannedAmount must be positive")
    private BigDecimal plannedAmount;

    @NotNull(message = "plannedDate is required")
    private LocalDate plannedDate;
}
