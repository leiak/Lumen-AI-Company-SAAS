package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SKU 主数据。code + barcode 在 (tenant_id, deleted) 内唯一。
 * status: active/inactive。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_item")
public class InvItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String sku;
    private String category;
    private String unit;
    private String spec;
    private String barcode;
    private String status;
    private Long tenantId;
}