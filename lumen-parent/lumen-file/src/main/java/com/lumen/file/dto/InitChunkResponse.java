package com.lumen.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitChunkResponse {
    /** 服务端分配的 uploadId，客户端后续 uploadPart/complete 需回传。 */
    private String uploadId;
    /** 真实分片大小 —— 若整文件小于 chunkSize，service 会自动归一化。 */
    private Long chunkSize;
    /** partNumber → 预签名 PUT URL；客户端可直接 PUT 到该 URL。 */
    private Map<Integer, String> presignedUrls;
}
