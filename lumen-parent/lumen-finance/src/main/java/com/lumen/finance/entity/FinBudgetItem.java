package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算明细：单个预算单下按科目拆分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_budget_item")
public class FinBudgetItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long budgetId;
    private Long subjectId;
    private BigDecimal plannedAmount;
    private BigDecimal usedAmount;
}
