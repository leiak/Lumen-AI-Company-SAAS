package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 签署任务。
 * signer_role ENUM: party_a/party_b/witness/internal
 * sign_method ENUM: electronic/wet/witness
 * status ENUM: pending/signed/rejected/expired
 * sign_provider ENUM: qiyuesuo/fadada/esign
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_sign_task")
public class SignTask extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long signerUserId;
    private String signerRole;
    private String signMethod;
    private String status;
    private String signProvider;
    private String externalTaskId;
    private LocalDateTime signedAt;
    private LocalDateTime expireAt;
    private Long fileId;
    private Long tenantId;
}
