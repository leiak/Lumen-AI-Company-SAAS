package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点单。status: planning=未开始 / in_progress=进行中 / completed=已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_stocktake")
public class AstStocktake extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String period;
    private Long departmentId;
    private LocalDateTime plannedAt;
    private LocalDateTime completedAt;
    private String status;
    private Integer diffCount;
}