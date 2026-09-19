package com.lumen.workflow.entity;

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
 * 流程实例。
 * status: 0=进行中 1=已完成 2=已取消
 * variables: JSON Map<String,Object> via {@link JacksonTypeHandler}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "wf_instance", autoResultMap = true)
public class WfInstance extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private String defKey;
    private String businessKey;
    private Long tenantId;
    private Integer status;
    private String currentNodeKey;

    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    private Long starter;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}