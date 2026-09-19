package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回款记录。一笔 receivable 可以有多个 payment_record,累加得 collected_amount。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_payment_record")
public class PaymentRecord extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receivableId;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private Long operatorId;
    private String remark;
    private Long tenantId;
}