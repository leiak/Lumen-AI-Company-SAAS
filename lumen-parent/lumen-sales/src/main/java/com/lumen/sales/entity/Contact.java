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

/**
 * 客户联系人。mobile/email 字段级加密。
 * status: active/inactive.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_contact")
public class Contact extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String name;
    private String position;

    @Encrypted
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobileEnc;

    @Encrypted
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String emailEnc;

    private String phone;
    private Boolean isPrimary;
    private String status;
    private Long tenantId;
}