package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库主表。status: active/inactive。
 * code 在 (tenant_id, deleted) 内唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_warehouse")
public class InvWarehouse extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String address;
    private Long managerId;
    private String status;
    private Long tenantId;
}