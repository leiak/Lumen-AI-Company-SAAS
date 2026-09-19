package com.lumen.contract.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SubmitContractRequest {
    @NotEmpty(message = "signerUserIds must not be empty")
    private List<Long> signerUserIds;

    @NotEmpty(message = "roles must not be empty")
    private List<String> roles;
}
