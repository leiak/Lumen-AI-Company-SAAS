package com.lumen.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户订阅。
 *
 * <p>One row per (user, eventType, channelCode). Drives which channels a user
 * wants notifications on for a given event type. {@link com.lumen.message.service.SubscriptionService}
 * pins {@code userId} from {@code UserContextHolder} on every write.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_subscription")
public class MsgSubscription extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String eventType;
    private String channelCode;
    private Integer enabled;
    private Long tenantId;
}