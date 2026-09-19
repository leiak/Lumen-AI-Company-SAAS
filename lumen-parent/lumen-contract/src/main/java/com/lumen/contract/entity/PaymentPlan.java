package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款/付款计划。
 * status ENUM: pending/partial/completed/overdue
 * planned_amount is immutable after creation (security requirement).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_payment_plan")
public class PaymentPlan extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private String planNo;
    private BigDecimal plannedAmount;
    private LocalDate plannedDate;
    private BigDecimal actualAmount;
    private LocalDate actualDate;
    private String status;
    private Long paymentId;
    private Long tenantId;
}
