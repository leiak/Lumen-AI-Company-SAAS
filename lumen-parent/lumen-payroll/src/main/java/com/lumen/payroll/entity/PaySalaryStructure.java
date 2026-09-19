package com.lumen.payroll.entity;

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
 * 薪资结构模板。
 * components: JSON via {@link JacksonTypeHandler} (e.g. baseSalary, basicAllowance,
 * positionAllowance, performanceBonus, insuranceBase, housingFundBase).
 * status: active/inactive.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_salary_structure", autoResultMap = true)
public class PaySalaryStructure extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;

    @TableField(value = "components", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> components;

    /** active / inactive */
    private String status;
}