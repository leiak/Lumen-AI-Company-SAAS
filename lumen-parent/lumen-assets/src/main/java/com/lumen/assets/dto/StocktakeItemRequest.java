package com.lumen.assets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StocktakeItemRequest {
    @NotNull
    private Long itemId;
    @NotNull
    private String actualStatus;
    private String actualLocation;
    private String note;
}