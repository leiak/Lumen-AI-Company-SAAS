package com.lumen.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveInvoiceRequest {

    @NotBlank(message = "invoiceNo is required")
    private String invoiceNo;

    /** vat_special / vat_general / electronic */
    @NotBlank(message = "invoiceType is required")
    private String invoiceType;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "taxAmount is required")
    private BigDecimal taxAmount;

    @NotNull(message = "issueDate is required")
    private LocalDate issueDate;

    private String sourceType;
    private Long sourceId;

    /** 敏感字段：原文由 service 加密后写入 */
    private String buyerName;
    private String sellerName;
    private String taxNo;
}
