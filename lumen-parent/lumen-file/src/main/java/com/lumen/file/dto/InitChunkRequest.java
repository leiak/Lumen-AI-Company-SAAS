package com.lumen.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitChunkRequest {

    /** 整文件 md5，用于秒传 / 校验。 */
    @NotBlank
    private String fileMd5;

    @NotBlank
    private String fileName;

    @NotNull
    @Min(1)
    private Integer totalChunks;

    /** 单片大小 (bytes)。客户端建议 5MB。 */
    @NotNull
    @Min(1)
    private Long chunkSize;

    @NotNull
    @Min(1)
    private Long totalSize;

    /** 业务类型，决定 content-type 白名单 (avatar/contract/expense/...) */
    private String businessType;

    /** 业务单据 ID —— 后续业务侧查询时定位。 */
    private String businessId;
}
