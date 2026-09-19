package com.lumen.bi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 保存报表请求。schedule != manual 时 cronExpression 必填且必须合法 cron 表达式
 * (service 层 cron-utils 二次校验)。
 */
@Data
public class SaveReportRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private Long datasetId;

    /** 报表模板 JSON */
    private Map<String, Object> template;

    @NotBlank(message = "schedule is required")
    @Pattern(regexp = "^(manual|daily|weekly|monthly)$",
        message = "schedule must be one of: manual/daily/weekly/monthly")
    private String schedule;

    /** cron 表达式 (Spring 6-field: 秒 分 时 日 月 周) */
    private String cronExpression;

    @NotBlank(message = "format is required")
    @Pattern(regexp = "^(pdf|excel|csv)$",
        message = "format must be one of: pdf/excel/csv")
    private String format;

    /** 收件人列表 */
    private List<String> recipients;

    /** active/inactive */
    private String status;
}