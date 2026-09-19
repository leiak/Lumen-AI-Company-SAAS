package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 车辆扩展（与 ast_asset 1:1）。plate_no 在 tenant 内 UNIQUE（soft-delete + plate_no）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_vehicle")
public class AstVehicle extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long assetId;
    private String plateNo;
    private String vehicleType;
    private Integer capacity;
    private Long currentMileage;
    private LocalDate lastMaintenanceAt;
    private Long nextMaintenanceMileage;
}