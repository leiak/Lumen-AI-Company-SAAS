package com.lumen.message.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /message/notification/send}.
 *
 * <p>Channel routing is implicit: the {@code templateCode} binds to a single
 * {@code channelCode} via {@link com.lumen.message.entity.MsgTemplate}.</p>
 */
@Data
public class SendNotificationRequest {

    /** Template code (looked up via {@code MsgTemplateMapper.findByCodeAndChannel}). */
    private String templateCode;

    /** Recipient user IDs. Each becomes its own notification row. */
    private List<Long> recipientUserIds;

    /**
     * Variable bindings for template substitution. Keys MUST be declared in the
     * template's {@code variables} array; missing keys → 400.
     */
    private Map<String, Object> variables;

    /**
     * Optional explicit channel code. If null, the service picks the first
     * enabled template+channel match. If set, must match the template's
     * channelCode.
     */
    private String channelCode;
}