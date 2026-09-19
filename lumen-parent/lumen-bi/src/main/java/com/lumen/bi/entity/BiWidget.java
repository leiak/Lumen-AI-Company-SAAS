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
 * 看板组件 (widget)。type: table/chart/number/gauge/pivot/filter。
 * datasetId / metricId 二选一,关联数据源。
 * config JSON: 组件配置 (颜色/格式/选项等)。
 * position JSON: {x,y,w,h} 位置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "bi_widget", autoResultMap = true)
public class BiWidget extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dashboardId;
    private String name;
    /** table/chart/number/gauge/pivot/filter */
    private String type;
    private Long datasetId;
    private Long metricId;
    /** JSON: 组件配置 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;
    /** JSON: {x,y,w,h} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> position;
    /** active/inactive */
    private String status;
}