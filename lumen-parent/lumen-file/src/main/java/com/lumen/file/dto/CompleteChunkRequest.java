package com.lumen.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteChunkRequest {
    @NotBlank
    private String uploadId;

    @NotNull
    @Min(1)
    private Integer totalChunks;
}
