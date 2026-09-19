package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 招投标参与方。
 * status ENUM: invited / joined / withdrew / rejected。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_bidding_participant")
public class ProcBiddingParticipant extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long biddingId;
    private Long supplierId;
    private BigDecimal bidAmount;
    private String status;
    private Long tenantId;
}