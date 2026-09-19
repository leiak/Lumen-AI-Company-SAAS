package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度折旧明细。UNIQUE(asset_id, period) — 同一期间不重复计提。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_depreciation")
public class AstDepreciation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long assetId;
    private String period;
    private BigDecimal depreciationAmount;
    private BigDecimal accumulatedAmount;
    private BigDecimal netValue;
    private LocalDateTime calculatedAt;
    private String method;
}