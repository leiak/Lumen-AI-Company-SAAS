package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资条。status: draft/calculated/confirmed/paid。
 * period: yyyy-MM。calculatedAt/confirmedAt/paidAt 记录状态变迁时间戳。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_slip")
public class PaySlip extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** yyyy-MM */
    private String period;
    private Long employeeId;
    private BigDecimal grossSalary;
    private BigDecimal totalDeduction;
    private BigDecimal netSalary;
    /** draft / calculated / confirmed / paid */
    private String status;
    private LocalDateTime calculatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime paidAt;
}