package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票。buyer/seller/tax_no 敏感字段由 {@link EncryptedStringTypeHandler}
 * 透明加解密；解密仅在 finance_admin 访问 /sensitive-info 时返回。
 * invoiceType: vat_special/vat_general/electronic.
 * recognizeStatus: pending/recognized/certified.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_invoice", autoResultMap = true)
public class FinInvoice extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String invoiceNo;
    /** vat_special / vat_general / electronic */
    private String invoiceType;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private LocalDate issueDate;
    private String sourceType;
    private Long sourceId;
    /** pending / recognized / certified */
    private String recognizeStatus;

    @TableField(value = "buyer_name_enc", typeHandler = EncryptedStringTypeHandler.class)
    private String buyerNameEnc;

    @TableField(value = "seller_name_enc", typeHandler = EncryptedStringTypeHandler.class)
    private String sellerNameEnc;

    @TableField(value = "tax_no_enc", typeHandler = EncryptedStringTypeHandler.class)
    private String taxNoEnc;
}
