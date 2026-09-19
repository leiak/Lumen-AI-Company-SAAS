package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 银行报盘生成请求。
 */
@Data
public class GenerateBankFileRequest {

    @NotBlank(message = "period is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
        message = "period must be yyyy-MM format (e.g. 2026-09)")
    private String period;

    @NotBlank(message = "bankCode is required")
    @Pattern(regexp = "^(ccb|icbc|cmb|abc|boc|spdb|comm|cib|pingan)$",
        message = "bankCode must be one of ccb/icbc/cmb/abc/boc/spdb/comm/cib/pingan")
    private String bankCode;
}