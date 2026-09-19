package com.lumen.mobile.entity;

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
 * 协作平台事件日志（仅追加，安全要求 #13）。
 *
 * <p>由 {@code IntegrationEventService} 写入；service 层不暴露 update / delete API。
 * 来自钉钉/企微/飞书的回调事件全部先入此表，再异步 process。</p>
 *
 * <p>UNIQUE(source_id, event_type, deleted) — 防重放（安全要求 #6）：
 * 同一 source_id + event_type 只能入一次，重复回调会被 UNIQUE 索引拒绝。</p>
 *
 * <p>platform: dingtalk / wechatwork / feishu。</p>
 * <p>processed: 0=待处理 1=已处理 2=失败。</p>
 * <p>payload: 事件原文 JSON（JacksonTypeHandler）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "int_event_log", autoResultMap = true)
public class IntegrationEventLog extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String platform;

    /** 如 "bpms_instance_change" / "bpms_task_change"。 */
    private String eventType;

    /** 平台侧事件唯一 ID。 */
    private String sourceId;

    @TableField(value = "payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** 0=待处理 1=已处理 2=失败。 */
    private Integer processed;

    private LocalDateTime processedAt;

    /** 处理失败时的错误信息（限制 VARCHAR 长度由 service 保证）。 */
    private String errorMessage;

    private Integer retryCount;

    /** 接收时间（区别于 create_time — create_time 由 BaseEntity 填充，received_at 来自事件时间）。 */
    private LocalDateTime receivedAt;
}
