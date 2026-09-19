package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同主表。
 * status ENUM: drafting/pending_approval/approved/signing/signed/fulfilling/expired/terminated/archived
 * type ENUM: sales/purchase/lease/service/employment/other
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_contract")
public class Contract extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String contractNo;
    private String title;
    private String type;
    private String partyA;
    private String partyB;
    private LocalDateTime partyASignedAt;
    private LocalDateTime partyBSignedAt;
    private BigDecimal amount;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long templateId;
    private Long drafterId;
    private Long workflowInstanceId;
    private Long fileId;
    private Long tenantId;
}
