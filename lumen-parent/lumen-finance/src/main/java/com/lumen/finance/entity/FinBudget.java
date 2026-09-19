package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算（按部门+科目聚合）。
 * period: yyyy-MM.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_budget")
public class FinBudget extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String period;
    private Long departmentId;
    private Long subjectId;
    private BigDecimal plannedAmount;
    private BigDecimal usedAmount;
    /** open / closed */
    private String status;
}
