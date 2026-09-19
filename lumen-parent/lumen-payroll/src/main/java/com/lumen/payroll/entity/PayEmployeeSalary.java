package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工薪资档案（历史）。每次调薪生成新行，靠 UNIQUE(employee_id, effective_from, deleted)
 * 保证同一员工同一生效日期唯一。status: active/inactive/suspended。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_employee_salary")
public class PayEmployeeSalary extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long employeeId;
    private Long structureId;
    private BigDecimal baseSalary;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    /** active / inactive / suspended */
    private String status;
}