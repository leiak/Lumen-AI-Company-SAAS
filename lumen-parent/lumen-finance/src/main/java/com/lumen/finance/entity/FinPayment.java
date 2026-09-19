package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付款单。
 * sourceType: payable/expense.
 * status: pending/done/failed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class FinPayment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String paymentNo;
    /** payable / expense */
    private String sourceType;
    private Long sourceId;
    private BigDecimal amount;
    private String payee;
    private String paymentMethod;
    private LocalDateTime paidAt;
    /** pending / done / failed */
    private String status;
}
