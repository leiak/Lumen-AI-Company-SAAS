package com.lumen.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelInstanceRequest {
    @Size(max = 500, message = "reason must not exceed 500 chars")
    private String reason;
}