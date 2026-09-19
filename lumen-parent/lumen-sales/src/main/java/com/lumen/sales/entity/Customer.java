package com.lumen.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客户。公海/私海模型。
 * <p>
 * level: A/B/C/D (1-4)
 * source: referral/ad/website/direct/event/other
 * status: in_pool/private/active/lost
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_customer")
public class Customer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String level;
    private String source;
    private Long ownerUserId;
    private String status;
    private String industry;
    private String scale;
    private String address;
    private LocalDateTime lastContactAt;
    private String lostReason;
    private Long tenantId;
}