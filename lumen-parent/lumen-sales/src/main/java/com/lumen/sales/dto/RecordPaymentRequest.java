package com.lumen.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPaymentRequest {
    @NotNull(message = "receivableId is required")
    private Long receivableId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be > 0")
    private BigDecimal amount;

    @NotNull(message = "paymentMethod is required")
    private String paymentMethod;

    private String remark;
}