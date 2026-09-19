package com.lumen.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReverseVoucherRequest {

    @NotBlank(message = "reason is required")
    private String reason;
}
