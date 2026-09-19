package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单员工社保计算请求。
 */
@Data
public class CalculateSocialSecurityRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotBlank(message = "period is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
        message = "period must be yyyy-MM format")
    private String period;

    @NotNull(message = "baseAmount is required")
    @Positive(message = "baseAmount must be positive")
    private BigDecimal baseAmount;
}