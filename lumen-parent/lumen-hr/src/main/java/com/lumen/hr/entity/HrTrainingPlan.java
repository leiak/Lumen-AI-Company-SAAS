package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 培训计划。
 * status: 0=草稿 1=已发布 2=进行中 3=已结束 4=已取消
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_training_plan")
public class HrTrainingPlan extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer capacity;
    private Integer status;
    private Long tenantId;
}
