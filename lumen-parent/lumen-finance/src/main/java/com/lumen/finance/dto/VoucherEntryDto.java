package com.lumen.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证明细。校验：subjectId 必填，debitAmount/creditAmount 二选一 > 0（service 层）。
 */
@Data
public class VoucherEntryDto {

    @NotNull(message = "subjectId is required")
    private Long subjectId;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    private String summary;
}
