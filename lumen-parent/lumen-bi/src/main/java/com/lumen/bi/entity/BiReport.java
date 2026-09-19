package com.lumen.bi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表。schedule: manual/daily/weekly/monthly。
 * format: pdf/excel/csv。
 * recipients JSON: 收件人列表 (邮箱)。
 * template JSON: 报表模板配置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "bi_report", autoResultMap = true)
public class BiReport extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private Long datasetId;
    /** JSON: 报表模板 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> template;
    /** manual/daily/weekly/monthly */
    private String schedule;
    private String cronExpression;
    /** pdf/excel/csv */
    private String format;
    /** JSON: 收件人列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> recipients;
    /** active/inactive */
    private String status;
    private LocalDateTime lastRunAt;
}