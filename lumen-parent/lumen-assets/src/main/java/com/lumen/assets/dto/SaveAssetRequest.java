package com.lumen.assets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asset 创建/更新请求体。originalValue 在 update 时被忽略（资产原值不可变）。
 */
@Data
public class SaveAssetRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    @NotNull
    private Long categoryId;
    @NotNull
    @PositiveOrZero
    private BigDecimal originalValue;
    private String depreciationMethod;
    private Integer usefulLifeMonths;
    @PositiveOrZero
    private BigDecimal salvageValue;
    private LocalDate purchaseDate;
    private Long deptId;
    private Long custodianId;
    private String status;
    private String qrCode;
    private String imageUrl;
}