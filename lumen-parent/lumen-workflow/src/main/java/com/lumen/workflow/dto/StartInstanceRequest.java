package com.lumen.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class StartInstanceRequest {
    @NotBlank(message = "defKey is required")
    private String defKey;

    @NotBlank(message = "businessKey is required")
    private String businessKey;

    /** Variables passed to the workflow; stored in wf_instance.variables JSON column. */
    private Map<String, Object> variables;
}