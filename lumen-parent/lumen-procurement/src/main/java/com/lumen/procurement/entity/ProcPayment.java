package com.lumen.procurement.entity;

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
 * sourceType ENUM: order / receipt。sourceId 指向对应订单或收货单。
 * payableId 是跨服务引用 (fin_payable.id)，P4 阶段不校验,留给 P5。
 * paymentMethod ENUM: cash / bank_transfer / check / other。
 * status ENUM: pending / approved / paid / rejected。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_payment")
public class ProcPayment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private String sourceType;
    private Long sourceId;
    private Long payableId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private Long requesterId;
    private Long approverId;
    private LocalDateTime paidAt;
    private Long tenantId;
}