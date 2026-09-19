package com.lumen.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DraftContractRequest {
    @NotNull(message = "templateId is required")
    private Long templateId;

    /** Variables passed to template.render — declared variables MUST be supplied (security requirement). */
    private Map<String, Object> variables;
}
