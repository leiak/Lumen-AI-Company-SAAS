package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 算薪请求。period 必须 yyyy-MM 格式。
 */
@Data
public class CalculatePayrollRequest {

    @NotBlank(message = "period is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
        message = "period must be yyyy-MM format (e.g. 2026-09)")
    private String period;
}