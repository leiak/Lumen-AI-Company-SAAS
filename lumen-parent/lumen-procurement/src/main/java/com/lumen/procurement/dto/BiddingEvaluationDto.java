package com.lumen.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 招投标综合评分返回。
 * 综合分 = 0.6 × 价格分 + 0.4 × 评级分 (与询价不同, 招投标权重偏向价格)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiddingEvaluationDto {
    private Long participantId;
    private Long supplierId;
    private String supplierName;
    private BigDecimal bidAmount;
    private BigDecimal supplierRating;
    private Double compositeScore;
    private BigDecimal overPriceRate;
    private String status;
}