package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出入库单。type: in/out/transfer。sourceType: purchase/sales/transfer/manual。
 * status: draft/confirmed/cancelled。sourceId 跨服务,留 TODO P5 接 Feign。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_inout")
public class InvInout extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String type;
    private String sourceType;
    private Long sourceId;
    private Long warehouseId;
    private Long targetWarehouseId;
    private Long operatorId;
    private LocalDate inoutDate;
    private String status;
    private String remark;
    private Long tenantId;
}