package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveItemRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    @NotBlank
    private String unit;
    private String sku;
    private String category;
    private String spec;
    private String barcode;
    private String status;
}