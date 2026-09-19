package com.lumen.contract.entity;

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
 * 合同变更日志。
 * change_type ENUM: draft/approve/sign/payment/terminate/other
 * before_value/after_value: JSON Map
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ctr_change_log", autoResultMap = true)
public class ChangeLog extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private String changeType;

    @TableField(value = "before_value", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> beforeValue;

    @TableField(value = "after_value", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> afterValue;

    private Long operatorId;
    private LocalDateTime operatedAt;
    private String comment;
    private Long tenantId;
}
