package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库位。type: storage/picking/receiving/shipping。
 * status: active/inactive。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_location")
public class InvLocation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private String code;
    private String name;
    private String type;
    private BigDecimal capacity;
    private String status;
    private Long tenantId;
}