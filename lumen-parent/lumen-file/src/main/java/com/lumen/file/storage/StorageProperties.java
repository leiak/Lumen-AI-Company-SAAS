package com.lumen.file.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶层配置 {@code lumen.storage.*}：
 * <ul>
 *   <li>{@code .max-file-size} —— service 层兜底上传大小 (bytes)</li>
 *   <li>{@code .max-presigned-expiry-seconds} —— 预签名 URL 时长上限</li>
 *   <li>{@code .default-presigned-expiry-seconds} —— 预签名 URL 默认时长</li>
 *   <li>{@code .minio.*} —— MinIO provider</li>
 *   <li>{@code .content-type-allow-list} —— 业务类型 → 允许 MIME</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumen.storage")
public class StorageProperties {

    private long maxFileSize = 104_857_600L;            // 100 MB
    private long maxPresignedExpirySeconds = 86_400L;   // 24 h
    private long defaultPresignedExpirySeconds = 3_600L; // 1 h

    private Minio minio = new Minio();

    /**
     * businessType → allowed MIME list. {@code null} businessType bypasses check.
     */
    private Map<String, List<String>> contentTypeAllowList = new HashMap<>();

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "lumen";
        /** 若 false，MinIO 不可达时启动会 log warn 但不抛异常。 */
        private boolean autoCreateBucket = true;
    }
}
