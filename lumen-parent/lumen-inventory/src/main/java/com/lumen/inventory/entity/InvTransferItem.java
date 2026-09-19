package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 调拨明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_transfer_item")
public class InvTransferItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transferId;
    private Long itemId;
    private String batchNo;
    private BigDecimal quantity;
    private Long tenantId;
}