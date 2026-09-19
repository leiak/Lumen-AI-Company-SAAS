package com.lumen.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateInquiryRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String title;
    @NotNull
    private LocalDate inquiryDate;
    @NotNull
    private LocalDate deadline;
    /** 拟邀请供应商 ID 列表 (draft 时可空, publish 时建议填入) */
    private List<Long> itemIds;
}