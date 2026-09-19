package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条库存移动 (入库/出库/锁定/解锁) 请求。
 */
@Data
public class StockMovementRequest {
    @NotNull
    private Long warehouseId;
    @NotNull
    private Long locationId;
    @NotNull
    private Long itemId;
    @NotBlank
    private String batchNo;
    @NotNull
    @Positive
    private BigDecimal quantity;
}