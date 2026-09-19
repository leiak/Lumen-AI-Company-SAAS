package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工资条已读记录。每条 pay_slip × employee 的已读时间戳，
 * UNIQUE(slip_id, employee_id, deleted) 保证每个员工只读一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_payslip_read")
public class PayPayslipRead extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long slipId;
    private Long employeeId;
    private LocalDateTime readAt;
}