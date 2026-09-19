package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.Encrypted;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售线索 (Lead)。
 * status: new/contacting/qualified/lost/converted.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_lead")
public class Lead extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerName;
    private String contactName;

    @Encrypted
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobileEnc;

    private String source;
    private String requirement;
    private BigDecimal estimatedValue;
    private Long ownerUserId;
    private String status;
    private Long convertedCustomerId;
    private LocalDateTime lastFollowupAt;
    private Long tenantId;
}