package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产卡片。status: in_stock=在库 / in_use=在用 / maintenance=维修中 / scrapped=已报废。
 * depreciation_method: straight_line=直线法 / double_declining=双倍余额 / sum_of_years=年数总和。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_asset")
public class AstAsset extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private Long categoryId;
    private BigDecimal originalValue;
    private BigDecimal currentValue;
    private String depreciationMethod;
    private Integer usefulLifeMonths;
    private BigDecimal salvageValue;
    private LocalDate purchaseDate;
    private Long deptId;
    private Long custodianId;
    private String status;
    private String qrCode;
    private String imageUrl;
}