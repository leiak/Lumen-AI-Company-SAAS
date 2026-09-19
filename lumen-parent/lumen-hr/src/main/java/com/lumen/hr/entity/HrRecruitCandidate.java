package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 候选人。
 * stage: 0=简历筛选 1=初试 2=复试 3=终试 4=已发 Offer 5=已入职 6=已淘汰
 * status: 0=进行中 1=已 Offer 2=已入职 3=已淘汰
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_candidate")
public class HrRecruitCandidate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String name;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobileEnc;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String emailEnc;

    private String resumeUrl;
    private Integer stage;
    private Integer status;
    private Long tenantId;
}
