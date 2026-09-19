package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量确认工资条请求。
 */
@Data
public class ConfirmPayslipRequest {

    @NotEmpty(message = "slipIds must not be empty")
    private List<Long> slipIds;
}