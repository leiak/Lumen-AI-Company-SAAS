package com.lumen.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息通知 (inbox + send log).
 *
 * <p>status: 0=pending 1=sent 2=failed 3=read.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_notification")
public class MsgNotification extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelCode;
    private String templateCode;
    private Long recipientUserId;
    private String recipientAddress;
    private String subject;
    private String content;
    private Integer status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime sentTime;
    private LocalDateTime readTime;
    private Long tenantId;
}