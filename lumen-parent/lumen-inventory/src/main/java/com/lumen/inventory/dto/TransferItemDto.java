package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferItemDto {
    @NotNull
    private Long itemId;
    private String batchNo;
    @NotNull
    @Positive
    private BigDecimal quantity;
}