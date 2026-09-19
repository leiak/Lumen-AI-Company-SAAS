package com.lumen.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveSubjectRequest {

    private Long parentId;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    /** asset / liability / equity / income / expense */
    @NotBlank(message = "type is required")
    private String type;

    /** debit / credit */
    @NotBlank(message = "balanceDirection is required")
    private String balanceDirection;

    private Integer level;

    private Integer status;
}
