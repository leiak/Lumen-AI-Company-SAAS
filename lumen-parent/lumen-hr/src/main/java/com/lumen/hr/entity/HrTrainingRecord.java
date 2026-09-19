package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训记录（员工报名 + 完成结果）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_training_record")
public class HrTrainingRecord extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long employeeId;
    private LocalDateTime completedAt;
    private BigDecimal score;
    private Long tenantId;
}
