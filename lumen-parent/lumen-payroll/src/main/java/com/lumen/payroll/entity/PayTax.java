package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 个税记录。special_deduction 加密存储（子女教育/住房贷款利息等敏感）。
 * taxRate 0~0.45 七级累进表中当前段税率。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_tax", autoResultMap = true)
public class PayTax extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long slipId;
    private Long employeeId;
    /** yyyy-MM */
    private String period;
    private BigDecimal taxableIncome;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private BigDecimal cumulativeIncome;
    private BigDecimal cumulativeTax;

    /**
     * 专项附加扣除 JSON: {children:1000, mortgage:1000, ...}
     * 字段级加密 (安全要求 #19).
     */
    @TableField(value = "special_deduction", typeHandler = EncryptedStringTypeHandler.class)
    private String specialDeductionJson;
}