package com.lumen.hr.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateJobRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "deptId is required")
    private Long deptId;

    @NotNull(message = "postId is required")
    private Long postId;

    @Min(value = 1, message = "headcount must be >= 1")
    private Integer headcount;
}
