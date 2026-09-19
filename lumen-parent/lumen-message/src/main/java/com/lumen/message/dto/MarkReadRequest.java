package com.lumen.message.dto;

import lombok.Data;

import java.util.List;

/**
 * Request body for {@code POST /message/notification/markReadBatch}.
 *
 * <p>Only IDs that belong to the calling user (within their tenant) are
 * updated — see {@link com.lumen.message.service.NotificationService#markReadBatch(java.util.List)}.</p>
 */
@Data
public class MarkReadRequest {
    private List<Long> ids;
}