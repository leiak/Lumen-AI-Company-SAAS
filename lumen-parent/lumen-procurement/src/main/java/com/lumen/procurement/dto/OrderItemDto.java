package com.lumen.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDto {
    @NotBlank
    private String itemName;
    private String sku;
    @NotNull
    @Positive
    private Integer quantity;
    @NotNull
    @Positive
    private BigDecimal unitPrice;
}