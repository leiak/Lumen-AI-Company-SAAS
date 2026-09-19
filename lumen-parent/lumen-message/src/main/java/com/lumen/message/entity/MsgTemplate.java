package com.lumen.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 消息模板。
 *
 * <p>variables: JSON array of variable names (e.g. {@code ["name","code"]}).
 * These names drive template substitution — any {{var}} in subject/content
 * MUST be declared here, or rendering throws 400.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "msg_template", autoResultMap = true)
public class MsgTemplate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String channelCode;
    private String subject;
    private String content;

    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private List<String> variables;

    private Integer enabled;
    private Long tenantId;
}