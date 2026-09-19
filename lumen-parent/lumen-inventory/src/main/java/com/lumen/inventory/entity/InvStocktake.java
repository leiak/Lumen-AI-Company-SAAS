package com.lumen.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点单。status: planning/in_progress/completed。
 * complete 必须所有 item 已 submit。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stocktake")
public class InvStocktake extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long warehouseId;
    private String period;
    private LocalDateTime plannedAt;
    private LocalDateTime completedAt;
    private String status;
    private Integer diffCount;
    private Long operatorId;
    private Long tenantId;
}