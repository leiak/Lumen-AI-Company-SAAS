package com.lumen.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveCustomerRequest {
    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String level;
    private String source;
    private String industry;
    private String scale;
    private String address;
}