package com.lumen.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * FIFO 扣减返回的明细 (从哪个 stock 扣减多少)。
 */
@Data
@AllArgsConstructor
public class StockDeductionDto {
    private Long stockId;
    private Long itemId;
    private Long warehouseId;
    private String batchNo;
    private BigDecimal deducted;
}