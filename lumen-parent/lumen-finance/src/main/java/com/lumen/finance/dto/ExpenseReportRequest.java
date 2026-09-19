package com.lumen.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ExpenseReportRequest {

    @NotNull(message = "departmentId is required")
    private Long departmentId;

    /** JSON array of {subjectId, amount, summary, ...} */
    private List<Map<String, Object>> items;

    @NotNull(message = "totalAmount is required")
    @Positive(message = "totalAmount must be positive")
    private BigDecimal totalAmount;
}
