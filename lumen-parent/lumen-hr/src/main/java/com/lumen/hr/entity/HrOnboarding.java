package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 入职清单。
 * checklist: JSON Map&lt;String, Boolean&gt; via {@link JacksonTypeHandler}.
 * status: 0=未开始 1=进行中 2=已完成
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "hr_onboarding", autoResultMap = true)
public class HrOnboarding extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;

    @TableField(value = "checklist", typeHandler = JacksonTypeHandler.class)
    private Map<String, Boolean> checklist;

    private Integer status;
    private LocalDateTime completedAt;
    private Long tenantId;
}
