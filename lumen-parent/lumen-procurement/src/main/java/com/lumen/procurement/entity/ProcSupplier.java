package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 供应商主表。
 * status ENUM: active / blacklist / pending。
 * level: 1-5 (5 最高)。rating: 0-5。contact_phone/email 加密存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_supplier")
public class ProcSupplier extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String taxNo;
    private Integer level;
    private String status;
    private String contactName;
    private String contactPhoneEnc;
    private String contactEmailEnc;
    private String address;
    private BigDecimal rating;
    private Long tenantId;
}