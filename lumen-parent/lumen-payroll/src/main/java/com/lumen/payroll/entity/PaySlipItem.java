package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工资条明细。itemType: earning/deduction。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_slip_item")
public class PaySlipItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long slipId;
    /** earning / deduction */
    private String itemType;
    private String itemCode;
    private String itemName;
    private BigDecimal amount;
    private String formula;
}