package com.lumen.assets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApplyCustodyRequest {
    @NotNull
    private Long assetId;
    @NotNull
    private Long custodianId;
    @NotNull
    private LocalDateTime startAt;
}