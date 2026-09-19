package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应商资质。
 * qualificationType ENUM: business_license / tax_registration / iso / other。
 * status ENUM: pending / approved / rejected / expired。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_supplier_qualification")
public class ProcSupplierQualification extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private String qualificationType;
    private Long fileId;
    private LocalDate expireAt;
    private String status;
    private Long tenantId;
}