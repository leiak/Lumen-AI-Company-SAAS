package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartStocktakeRequest {
    @NotBlank
    private String code;
    @NotNull
    private Long warehouseId;
    @NotBlank
    private String period;
}