package com.lumen.hr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TransferRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "toDeptId is required")
    private Long toDeptId;

    @NotNull(message = "toPostId is required")
    private Long toPostId;

    @NotNull(message = "effectiveAt is required")
    private LocalDate effectiveAt;

    private String comment;
}
