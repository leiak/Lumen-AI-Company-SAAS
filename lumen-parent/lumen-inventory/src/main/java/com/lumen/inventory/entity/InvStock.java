package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存（按仓库+库位+SKU+批次）。
 * UNIQUE(warehouse_id, location_id, item_id, batch_no, deleted) 保证唯一。
 * quantity = available_quantity + locked_quantity。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock")
public class InvStock extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long locationId;
    private Long itemId;
    private String batchNo;
    private BigDecimal quantity;
    private BigDecimal availableQuantity;
    private BigDecimal lockedQuantity;
    private LocalDateTime lastInAt;
    private LocalDateTime lastOutAt;
    private Long tenantId;
}