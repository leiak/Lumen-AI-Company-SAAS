package com.lumen.procurement.entity;

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
 * 采购单。
 * sourceType ENUM: quotation / bidding / direct。
 * status ENUM: draft / submitted / approved / rejected / fulfilled / cancelled。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_order")
public class ProcOrder extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long supplierId;
    private String sourceType;
    private Long sourceId;
    private BigDecimal totalAmount;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryAt;
    private String status;
    private Long approverId;
    private LocalDateTime approvedAt;
    private Long tenantId;
}