package com.lumen.file.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 对象存储抽象 — 当前唯一实现 {@link MinIoStorageProvider}，但接口保留以便
 * 切换 S3/OSS/本地 FS。
 */
public interface StorageProvider {

    /** 普通上传：把 stream 写入 {@code bucket/key}，返回写入字节数。 */
    long put(String bucket, String key, InputStream stream, long size, String contentType);

    /** 读取对象，返回输入流。调用方负责关闭。 */
    InputStream get(String bucket, String key);

    /** 删除单个对象。 */
    void delete(String bucket, String key);

    /** 批量删除（用于 abort 分片上传）。 */
    void deleteAll(String bucket, List<String> keys);

    /**
     * 生成预签名下载 URL。{@code expiry} 必须 &gt; 0 且在调用方已经过 clamp。
     */
    String presignedUrl(String bucket, String key, Duration expiry);

    /** 触发分片上传初始化，返回 MinIO 服务端分配的 uploadId。 */
    String initMultipartUpload(String bucket, String key, String contentType);

    /**
     * 写入单个分片。返回 ETag。
     * @param uploadId    从 {@link #initMultipartUpload} 拿到
     * @param partNumber  从 1 开始
     */
    String uploadPart(String bucket, String key, String uploadId, int partNumber,
                      InputStream stream, long size);

    /**
     * 完成分片上传。{@code parts} 是 partNumber → ETag。
     */
    void completeMultipartUpload(String bucket, String key, String uploadId,
                                 Map<Integer, String> parts);

    /** 中止分片上传。 */
    void abortMultipartUpload(String bucket, String key, String uploadId);

    /** 健康检查 —— 启动时若返回 false，应用仍然启动但相关接口会 503。 */
    boolean isHealthy();
}
