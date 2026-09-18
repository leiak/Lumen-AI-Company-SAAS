package com.lumen.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant")
public class Tenant extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String industry;
    private String scale;
    private String region;
    private Long packageId;
    private Integer status;
    private Integer trialDays;
    private LocalDateTime expireAt;
    private LocalDateTime activatedAt;
    private String logoUrl;
    private String description;
}
