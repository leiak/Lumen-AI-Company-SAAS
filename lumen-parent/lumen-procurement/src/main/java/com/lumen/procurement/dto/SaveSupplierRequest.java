package com.lumen.procurement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaveSupplierRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    @NotBlank
    private String taxNo;
    @Min(1) @Max(5)
    private Integer level;
    private String status;
    private String contactName;
    private String contactPhoneEnc;
    private String contactEmailEnc;
    private String address;
    @DecimalMin("0.00") @DecimalMax("5.00")
    private BigDecimal rating;
}