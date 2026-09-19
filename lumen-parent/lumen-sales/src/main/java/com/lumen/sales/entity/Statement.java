package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户对账单。按客户 + 期间汇总订单/回款。
 * status: draft/sent/confirmed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_statement")
public class Statement extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime generatedAt;
    private LocalDateTime sentAt;
    private Long tenantId;
}