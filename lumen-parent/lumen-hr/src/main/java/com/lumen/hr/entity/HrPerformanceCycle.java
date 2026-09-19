package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 绩效周期。
 * status: 0=未开始 1=进行中 2=已结束
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_performance_cycle")
public class HrPerformanceCycle extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer status;
    private Long tenantId;
}
