package com.lumen.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateTransferRequest {
    @NotNull
    private Long fromWarehouseId;
    @NotNull
    private Long toWarehouseId;
    @NotNull
    private LocalDate transferDate;
    private String remark;
    @Valid
    private List<TransferItemDto> items;
}