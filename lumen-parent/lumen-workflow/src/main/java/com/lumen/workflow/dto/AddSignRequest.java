package com.lumen.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AddSignRequest {
    @NotEmpty(message = "userIds must not be empty")
    private List<Long> userIds;

    @Size(max = 1000, message = "comment must not exceed 1000 chars")
    private String comment;
}