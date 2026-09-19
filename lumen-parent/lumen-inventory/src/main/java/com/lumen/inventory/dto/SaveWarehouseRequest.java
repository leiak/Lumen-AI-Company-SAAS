package com.lumen.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveWarehouseRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String address;
    private Long managerId;
    private String status;
}