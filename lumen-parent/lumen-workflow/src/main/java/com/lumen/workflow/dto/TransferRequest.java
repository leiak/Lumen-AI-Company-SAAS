package com.lumen.workflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferRequest {
    @NotNull(message = "toUserId is required")
    private Long toUserId;

    @Size(max = 1000, message = "comment must not exceed 1000 chars")
    private String comment;
}