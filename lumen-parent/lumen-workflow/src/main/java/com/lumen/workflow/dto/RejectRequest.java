package com.lumen.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRequest {
    @NotBlank(message = "targetNodeKey is required")
    private String targetNodeKey;

    @Size(max = 1000, message = "comment must not exceed 1000 chars")
    private String comment;
}