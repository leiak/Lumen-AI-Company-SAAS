package com.lumen.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyPaymentRequest {
    @NotBlank
    private String paymentNo;
    /** order / receipt */
    @NotNull
    private String sourceType;
    @NotNull
    private Long sourceId;
    private Long payableId;
    @NotNull
    @Positive
    private BigDecimal amount;
    /** cash / bank_transfer / check / other */
    @NotBlank
    private String paymentMethod;
}