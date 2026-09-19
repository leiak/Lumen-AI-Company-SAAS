package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 合同附件。
 * attachment_type ENUM: main_contract/supplementary/invoice/other
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_attachment")
public class Attachment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long fileId;
    private String attachmentType;
    private LocalDateTime uploadedAt;
    private Long tenantId;
}
