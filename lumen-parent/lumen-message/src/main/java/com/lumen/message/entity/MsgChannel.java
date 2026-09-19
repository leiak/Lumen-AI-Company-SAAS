package com.lumen.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 消息渠道。
 *
 * <p>type: email/sms/site/dingtalk/wechat/webhook.</p>
 * <p>config: JSON map of channel-specific credentials (SMTP host/port/user/pass
 * for email; webhook URL for dingtalk/wechat/webhook; accessKey for SMS).
 * The {@code config} JSON MUST NOT contain raw credentials in production;
 * use jasypt or Nacos encrypted config (enforcement is TODO).</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "msg_channel", autoResultMap = true)
public class MsgChannel extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String type;

    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    private Integer enabled;
    private Long tenantId;
}