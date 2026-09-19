package com.lumen.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 凭证保存请求。entries 在 service 层做 XOR 校验（同一行借贷不能同时 > 0）。
 */
@Data
public class SaveVoucherRequest {

    @NotBlank(message = "period is required (yyyy-MM)")
    private String period;

    @NotNull(message = "voucherDate is required")
    private LocalDate voucherDate;

    @NotBlank(message = "summary is required")
    private String summary;

    @NotEmpty(message = "entries must not be empty")
    @Valid
    private List<VoucherEntryDto> entries;
}
