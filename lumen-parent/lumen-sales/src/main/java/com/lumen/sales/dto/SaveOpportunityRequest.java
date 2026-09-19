package com.lumen.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveOpportunityRequest {
    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "customerId is required")
    private Long customerId;

    private Long leadId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0", inclusive = true, message = "amount must be >= 0")
    private BigDecimal amount;

    @DecimalMin(value = "0", inclusive = true, message = "probability must be 0-100")
    @DecimalMax(value = "100", inclusive = true, message = "probability must be 0-100")
    private Integer probability;

    @NotNull(message = "expectedCloseDate is required")
    private LocalDate expectedCloseDate;
}