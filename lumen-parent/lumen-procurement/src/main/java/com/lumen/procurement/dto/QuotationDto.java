package com.lumen.procurement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class QuotationDto {
    @NotNull
    private Long supplierId;
    @NotNull
    @Positive
    private BigDecimal totalAmount;
    private LocalDate validUntil;
    @NotNull
    @Positive
    private Integer leadTimeDays;
    private String paymentTerms;
}