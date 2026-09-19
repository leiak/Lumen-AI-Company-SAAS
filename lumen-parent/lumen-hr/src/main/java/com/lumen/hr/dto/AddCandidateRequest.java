package com.lumen.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCandidateRequest {

    @NotNull(message = "jobId is required")
    private Long jobId;

    @NotBlank(message = "name is required")
    private String name;

    private String mobileEnc;
    private String emailEnc;
    private String resumeUrl;
}
