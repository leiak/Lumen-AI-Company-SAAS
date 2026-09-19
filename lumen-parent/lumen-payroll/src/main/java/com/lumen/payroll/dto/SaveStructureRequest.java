package com.lumen.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 薪资结构保存请求。components 必须含 baseSalary key (service 校验)。
 */
@Data
public class SaveStructureRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    /**
     * 薪资组成 JSON. 必须包含 baseSalary key; 其他键可选:
     * basicAllowance / positionAllowance / performanceBonus / insuranceBase / housingFundBase.
     */
    private Map<String, Object> components;

    /** active / inactive; 默认 active */
    private String status;
}