package com.lumen.bi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 保存看板请求。
 */
@Data
public class SaveDashboardRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    /** 看板布局 JSON */
    private Map<String, Object> layout;

    /** 关联 widget id 列表 */
    private List<Long> widgetIds;

    /** draft/published/archived */
    private String status;
}