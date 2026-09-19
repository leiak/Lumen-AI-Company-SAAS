package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 询价单。
 * status ENUM: draft / published / closed / awarded。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_inquiry")
public class ProcInquiry extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private LocalDate inquiryDate;
    private LocalDate deadline;
    private String status;
    private Long creatorId;
    private LocalDateTime publishedAt;
    private Long tenantId;
}