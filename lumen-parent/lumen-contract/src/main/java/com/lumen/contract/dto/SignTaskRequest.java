package com.lumen.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignTaskRequest {
    @NotNull(message = "contractId is required")
    private Long contractId;

    @NotNull(message = "signerUserId is required")
    private Long signerUserId;

    /** party_a / party_b / witness / internal */
    @NotBlank(message = "role is required")
    private String role;

    /** electronic / wet / witness */
    @NotBlank(message = "method is required")
    private String method;

    /** qiyuesuo / fadada / esign */
    private String provider;
}
