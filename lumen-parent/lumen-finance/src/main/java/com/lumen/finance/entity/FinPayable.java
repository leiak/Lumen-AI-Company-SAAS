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
 * 应付单。
 * sourceType: purchase/other.
 * status: pending/partial/paid.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payable")
public class FinPayable extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long supplierId;
    /** purchase / other */
    private String sourceType;
    private Long sourceId;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    /** pending / partial / paid */
    private String status;
}
