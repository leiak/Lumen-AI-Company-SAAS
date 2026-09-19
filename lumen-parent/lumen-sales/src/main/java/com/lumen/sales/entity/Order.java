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
 * 销售订单。
 * sourceType: opportunity/quotation/direct.
 * status: draft/confirmed/shipping/shipped/completed/cancelled.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_order")
public class Order extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long customerId;
    /** 关联合同(可选)— 跨服务,留 TODO P5 接 contract Feign */
    private Long contractId;
    private String sourceType;
    private Long sourceId;
    private BigDecimal totalAmount;
    private LocalDate orderDate;
    private String status;
    private Long tenantId;
}