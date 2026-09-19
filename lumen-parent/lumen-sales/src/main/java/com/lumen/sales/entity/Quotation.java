package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报价单。多版本,每次新版本基于上一版 items。
 * status: draft/sent/accepted/rejected/expired.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_quotation")
public class Quotation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long opportunityId;
    private Integer version;
    private BigDecimal totalAmount;
    private LocalDate validUntil;
    private String terms;
    private String status;
    private Long tenantId;
}