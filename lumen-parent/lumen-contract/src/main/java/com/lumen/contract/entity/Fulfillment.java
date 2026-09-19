package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 履约里程碑记录。
 * status ENUM: pending/in_progress/completed/overdue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_fulfillment")
public class Fulfillment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private String milestoneName;
    private LocalDate plannedDate;
    private LocalDate completedDate;
    private String status;
    private Long evidenceFileId;
    private String note;
    private Long tenantId;
}
