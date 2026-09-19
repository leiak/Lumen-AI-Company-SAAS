package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工薪资保存请求。effectiveTo (如有) 必须 > effectiveFrom (service 校验)。
 */
@Data
public class SaveEmployeeSalaryRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "structureId is required")
    private Long structureId;

    @NotNull(message = "baseSalary is required")
    @Positive(message = "baseSalary must be positive")
    private BigDecimal baseSalary;

    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /** active / inactive / suspended; 默认 active */
    private String status;
}