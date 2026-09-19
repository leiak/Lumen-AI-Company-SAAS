package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出入库明细。unit_price/subtotal 用 BigDecimal,15-17 项安全要求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_inout_item")
public class InvInoutItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inoutId;
    private Long itemId;
    private String batchNo;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private Long tenantId;
}