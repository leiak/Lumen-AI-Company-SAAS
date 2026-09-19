package com.lumen.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招投标主表。
 * type ENUM: public / invited。
 * status ENUM: draft / published / evaluating / awarded / closed。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_bidding")
public class ProcBidding extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private String type;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private Long tenantId;
}