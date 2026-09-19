package com.lumen.procurement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AwardInquiryRequest {
    @NotNull
    private Long selectedQuotationId;
}