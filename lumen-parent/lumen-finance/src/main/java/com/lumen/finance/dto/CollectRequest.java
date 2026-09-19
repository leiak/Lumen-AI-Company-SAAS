package com.lumen.finance.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CollectRequest {

    @Positive(message = "amount must be positive")
    private BigDecimal amount;
}
