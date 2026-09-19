package com.lumen.assets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TransferRequest {
    @NotNull
    private Long assetId;
    @NotNull
    private Long toDeptId;
    @NotNull
    private Long toCustodianId;
    private LocalDate transferDate;
    private String reason;
}