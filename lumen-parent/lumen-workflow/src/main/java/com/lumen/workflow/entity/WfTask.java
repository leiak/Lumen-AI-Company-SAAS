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
import java.util.List;

/**
 * 待办任务。
 * status: 0=待办 1=已办 2=已转办 3=已加签 4=已驳回
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "wf_task", autoResultMap = true)
public class WfTask extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private Long assignee;

    @TableField(value = "candidate_users", typeHandler = JacksonTypeHandler.class)
    private List<Long> candidateUsers;

    @TableField(value = "candidate_roles", typeHandler = JacksonTypeHandler.class)
    private List<String> candidateRoles;

    private Integer status;
    private LocalDateTime dueTime;
    private LocalDateTime completeTime;
    private String comment;
}