package com.lumen.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationItemDto {
    @NotBlank(message = "itemName is required")
    private String itemName;

    private String sku;

    @Positive(message = "quantity must be > 0")
    private Integer quantity;

    @Positive(message = "unitPrice must be > 0")
    private BigDecimal unitPrice;
}