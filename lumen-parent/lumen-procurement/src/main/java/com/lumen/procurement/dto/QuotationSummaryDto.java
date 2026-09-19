package com.lumen.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 询比价分析返回结构。
 * 综合分 (compositeScore) = 0.7 × 价格得分 + 0.3 × 评级得分
 *   价格得分 = (maxAmount - totalAmount) / (maxAmount - minAmount)  (1=最低,0=最高)
 *   评级得分 = supplierRating / 5
 * compositeScore 越大越优。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotationSummaryDto {
    private Long quotationId;
    private Long supplierId;
    private String supplierName;
    private BigDecimal totalAmount;
    private Integer leadTimeDays;
    private BigDecimal supplierRating;
    /** 综合分 0-1, 综合 amount(70%) + rating(30%) */
    private Double compositeScore;
    /** 该报价相对最低价的上浮率 (0% = 最低价) */
    private BigDecimal overPriceRate;
    private boolean selected;
}