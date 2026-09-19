package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 发货单。
 * status: pending/in_transit/delivered/exception.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_shipment")
public class Shipment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long orderId;
    private LocalDate shipmentDate;
    private String carrier;
    private String trackingNo;
    private String status;
    private Long tenantId;
}