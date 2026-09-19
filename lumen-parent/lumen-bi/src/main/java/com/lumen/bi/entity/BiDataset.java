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
 * 数据集。source_type: sql/api/join。
 * model JSON: 数据模型配置 (字段列表/类型/关联等)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "bi_dataset", autoResultMap = true)
public class BiDataset extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** sql/api/join */
    private String sourceType;
    /** JSON: 数据模型 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> model;
    /** active/inactive */
    private String status;
}