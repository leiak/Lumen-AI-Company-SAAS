package com.lumen.bi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 指标定义。category: sales/finance/hr/procurement/inventory/custom。
 * definition JSON: {sql, params, dimensions, measures, filters}。
 * status: active/inactive。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "bi_metric", autoResultMap = true)
public class BiMetric extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** sales/finance/hr/procurement/inventory/custom */
    private String category;
    /** JSON: {sql, params, dimensions, measures, filters} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> definition;
    /** active/inactive */
    private String status;
    private Integer version;
}