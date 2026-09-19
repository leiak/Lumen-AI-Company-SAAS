package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 盘点明细。system_quantity 来自库存 snapshot,actual_quantity 由盘点人员录入,
 * diff_quantity = actual - system,submitted 标记是否已 submit。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stocktake_item")
public class InvStocktakeItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
    private Long itemId;
    private Long warehouseId;
    private String batchNo;
    private BigDecimal systemQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal diffQuantity;
    private Integer submitted;
    private Long tenantId;
}