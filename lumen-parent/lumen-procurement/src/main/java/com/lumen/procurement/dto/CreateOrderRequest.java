package com.lumen.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotBlank
    private String code;
    @NotNull
    private Long supplierId;
    /** quotation / bidding / direct */
    @NotNull
    private String sourceType;
    /** 关联 source 的 id; direct 时可空 */
    private Long sourceId;
    @NotNull
    private LocalDate orderDate;
    private LocalDate expectedDeliveryAt;
    @Valid
    private List<OrderItemDto> items;
}