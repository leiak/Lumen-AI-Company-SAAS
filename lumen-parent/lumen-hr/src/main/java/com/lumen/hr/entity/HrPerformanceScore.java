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
 * 绩效评分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_performance_score")
public class HrPerformanceScore extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cycleId;
    private Long employeeId;
    private BigDecimal score;
    private String comment;
    private LocalDateTime submittedAt;
    private Long tenantId;
}
