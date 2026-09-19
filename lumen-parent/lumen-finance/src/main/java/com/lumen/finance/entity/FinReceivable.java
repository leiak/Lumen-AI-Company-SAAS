package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收单。
 * sourceType: sales/other.
 * status: pending/partial/collected.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable")
public class FinReceivable extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long customerId;
    /** sales / other */
    private String sourceType;
    private Long sourceId;
    private BigDecimal amount;
    private BigDecimal collectedAmount;
    private LocalDate dueDate;
    /** pending / partial / collected */
    private String status;
}
