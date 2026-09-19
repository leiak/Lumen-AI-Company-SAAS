package com.lumen.bi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * 保存指标请求。category 限定白名单 (controller @PreAuthorize 已限制角色,
 * 此处 service.parseDefinition 二次校验 definition 必须包含 sql/dimensions/measures)。
 */
@Data
public class SaveMetricRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "category is required")
    @Pattern(regexp = "^(sales|finance|hr|procurement|inventory|custom)$",
        message = "category must be one of: sales/finance/hr/procurement/inventory/custom")
    private String category;

    /** 指标定义 JSON: {sql, params, dimensions, measures, filters} */
    private Map<String, Object> definition;

    /** active/inactive; 默认 active */
    private String status;
}