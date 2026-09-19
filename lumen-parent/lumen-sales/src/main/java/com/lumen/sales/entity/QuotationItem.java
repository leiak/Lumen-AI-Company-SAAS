package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报价单明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_quotation_item")
public class QuotationItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long quotationId;
    private String itemName;
    private String sku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private Long tenantId;
}