package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报价单。
 * status ENUM: submitted / selected / rejected / withdrawn。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_quotation")
public class ProcQuotation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inquiryId;
    private Long supplierId;
    private BigDecimal totalAmount;
    private LocalDate validUntil;
    private Integer leadTimeDays;
    private String paymentTerms;
    private String status;
    private Long tenantId;
}