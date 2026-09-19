package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 合同条款模板。
 * status: 1=启用 0=禁用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ctr_clause_template", autoResultMap = true)
public class ClauseTemplate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String type;

    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private List<String> variables;

    private Integer status;
    private Long tenantId;
}
