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
 * 销售机会。
 * stage: qualification/proposal/negotiation/won/lost.
 * status: open/won/lost.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_opportunity")
public class Opportunity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long customerId;
    private Long leadId;
    private String stage;
    private BigDecimal amount;
    private Integer probability;
    private LocalDate expectedCloseDate;
    private Long ownerUserId;
    private String status;
    private String closeReason;
    private Long tenantId;
}