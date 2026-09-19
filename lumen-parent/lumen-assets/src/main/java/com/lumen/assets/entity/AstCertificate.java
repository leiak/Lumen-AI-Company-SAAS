package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 证照（营业执照 / 税务登记 / ISO资质 / 其他）。
 * certificate_type: business_license / tax_registration / iso_qualification / other。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_certificate")
public class AstCertificate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String certificateType;
    private String certificateNo;
    private String holder;
    private LocalDate issueDate;
    private LocalDate expireAt;
    private String fileUrl;
    private String status;
}