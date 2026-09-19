package com.lumen.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 出入库单创建请求。
 * type: in/out/transfer。sourceType: purchase/sales/transfer/manual。
 */
@Data
public class CreateInoutRequest {
    @NotNull
    private String type;
    @NotNull
    private String sourceType;
    private Long sourceId;
    @NotNull
    private Long warehouseId;
    /** type=transfer 时必填 (实际在 service 中校验) */
    private Long targetWarehouseId;
    @NotNull
    private LocalDate inoutDate;
    private String remark;
    @Valid
    private List<InoutItemDto> items;
}