package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购单明细。
 * received_quantity: 累计收货数量 (累加自 proc_receipt.confirm)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_order_item")
public class ProcOrderItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String itemName;
    private String sku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private Integer receivedQuantity;
    private Long tenantId;
}