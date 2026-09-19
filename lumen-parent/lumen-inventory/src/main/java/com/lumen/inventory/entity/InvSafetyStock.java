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
 * 安全库存。alert_status: normal/low/out_of_stock/overstock。
 * check() 后更新 current_quantity + alert_status + last_alert_at。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_safety_stock")
public class InvSafetyStock extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long itemId;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private BigDecimal currentQuantity;
    private String alertStatus;
    private LocalDateTime lastAlertAt;
    private Long tenantId;
}