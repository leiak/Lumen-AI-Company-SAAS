package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 调拨单。status: draft/in_transit/received/cancelled。
 * ship → in_transit (源仓库扣减); receive → received (目标仓库增加)。原子事务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_transfer")
public class InvTransfer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private LocalDate transferDate;
    private String status;
    private Long operatorId;
    private String remark;
    private Long tenantId;
}