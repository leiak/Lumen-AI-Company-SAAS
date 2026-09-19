package com.lumen.hr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SendOfferRequest {

    @NotNull(message = "candidateId is required")
    private Long candidateId;

    @NotNull(message = "salary is required")
    private BigDecimal salary;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;
}
