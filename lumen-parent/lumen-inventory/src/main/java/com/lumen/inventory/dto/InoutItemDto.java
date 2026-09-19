package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InoutItemDto {
    @NotNull
    private Long itemId;
    private String batchNo;
    @NotNull
    @Positive
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}