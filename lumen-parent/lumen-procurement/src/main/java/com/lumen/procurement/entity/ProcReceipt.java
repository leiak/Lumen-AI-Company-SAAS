package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 收货单。
 * status ENUM: pending / confirmed / discrepancy (差异)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_receipt")
public class ProcReceipt extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long orderId;
    private LocalDate receiptDate;
    private String status;
    private Long inspectorId;
    private Long tenantId;
}