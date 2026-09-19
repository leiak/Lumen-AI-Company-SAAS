package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StocktakeItemRequest {
    @NotNull
    private Long itemId;
    @PositiveOrZero
    private BigDecimal actualQuantity;
    private String note;
}