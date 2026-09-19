package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * 协作平台回调事件入参（管理员/集成回调使用）。
 * sourceId 由平台提供的事件唯一 ID，配合 eventType 防重放（UNIQUE 索引）。
 */
@Data
public class ReceiveEventRequest {

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(dingtalk|wechatwork|feishu)$",
        message = "platform must be one of: dingtalk / wechatwork / feishu")
    private String platform;

    @NotBlank(message = "eventType is required")
    private String eventType;

    /** 平台侧事件唯一 ID；用于防重放（UNIQUE(source_id, event_type, deleted)）。 */
    private String sourceId;

    /** 事件原文 JSON。 */
    private Map<String, Object> payload;
}
