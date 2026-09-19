package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收账款。
 * status: pending/partial/collected/overdue.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_receivable")
public class Receivable extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private BigDecimal collectedAmount;
    private Long tenantId;
}