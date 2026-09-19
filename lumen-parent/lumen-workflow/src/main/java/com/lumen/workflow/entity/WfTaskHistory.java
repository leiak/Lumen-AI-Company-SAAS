package com.lumen.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务操作历史归档。
 * PK is auto-increment; populated on task completion (done / transfer / addSign / reject).
 */
@Data
@TableName("wf_task_history")
public class WfTaskHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private Long assignee;
    /** done / transfer / addSign / reject */
    private String action;
    private String comment;
    private Long operatedBy;
    private LocalDateTime operatedTime;
}