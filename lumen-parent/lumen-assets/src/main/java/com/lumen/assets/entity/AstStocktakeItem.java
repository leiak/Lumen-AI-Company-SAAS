package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 盘点单明细（每张资产卡一行）。
 * diff_type: none=无差异 / lost=丢失 / extra=多出 / moved=位置变更 / damaged=损坏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_stocktake_item")
public class AstStocktakeItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long stocktakeId;
    private Long assetId;
    private String expectedLocation;
    private String actualLocation;
    private String expectedStatus;
    private String actualStatus;
    private String diffType;
    private String note;
}