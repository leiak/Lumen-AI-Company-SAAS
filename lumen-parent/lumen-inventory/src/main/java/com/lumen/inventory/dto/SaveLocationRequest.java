package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaveLocationRequest {
    @NotNull
    private Long warehouseId;
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    /** storage/picking/receiving/shipping */
    private String type;
    private BigDecimal capacity;
    private String status;
}