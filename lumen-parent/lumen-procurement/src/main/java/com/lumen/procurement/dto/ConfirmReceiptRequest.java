package com.lumen.procurement.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ConfirmReceiptRequest {
    /** itemId -> 实际收货数量 */
    private Map<Long, Long> actualQuantities;
    private Long inspectorId;
}