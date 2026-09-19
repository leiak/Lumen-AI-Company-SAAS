package com.lumen.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.file.entity.FileMetadata;
import com.lumen.file.mapper.FileMetadataMapper;
import com.lumen.file.storage.StorageProperties;
import com.lumen.file.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件 service —— 负责上传/下载/删除/预签名的业务规则。
 *
 * <p>全部存储操作通过 {@link StorageProvider} 抽象，便于测试和替换后端。
 * Tenant scoping 与上传权限由 service 层在 UserContext 上做最终校验
 * （不能仅依赖 TenantLineInnerInterceptor，因为接口同时承担授权）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** Mirrors {@code EngineService.SUPER_ADMIN_ROLE}. Local to avoid cross-module dep. */
    public static final String SUPER_ADMIN_ROLE = "super_admin";

    /** 8 KB — 安全要求 #11：禁止整文件 in-memory。 */
    private static final int STREAM_BUFFER = 8 * 1024;

    private final FileMetadataMapper metadataMapper;
    private final StorageProvider storage;
    private final StorageProperties properties;

    // ---------------------------------------------------------------
    // upload
    // ---------------------------------------------------------------

    /**
     * 上传一个完整文件。先计算 md5+sha256 (流式，8KB buffer)，按租户查重，
     * 然后写入 MinIO 并落 file_metadata 行。
     */
    @Transactional
    public FileMetadata upload(MultipartFile file, String businessType, String businessId) {
        UserContext ctx = requireUserContext();
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "file is required and must not be empty");
        }
        // 安全要求 #3：service-level size cap (defense in depth — servlet 也有 multipart cap)
        long size = file.getSize();
        long cap = properties.getMaxFileSize();
        if (size > cap) {
            throw new ServiceException(413,
                "File exceeds size limit: " + size + " > " + cap + " bytes");
        }
        String contentType = file.getContentType();
        // 安全要求 #4：content-type 白名单 (businessType=null 时跳过)
        assertContentTypeAllowed(businessType, contentType);

        try {
            String md5;
            String sha256;
            // 流式 hash：先用 InputStream 求 md5+sha256 再传给 putObject。
            try (InputStream in = file.getInputStream()) {
                Hashes hashes = hashStream(in);
                md5 = hashes.md5;
                sha256 = hashes.sha256;
            }

            // 安全要求 #6：tenant-scoped dedupe —— 跨租户不去重
            FileMetadata existing = metadataMapper.selectByMd5AndTenant(md5, ctx.getTenantId());
            if (existing != null) {
                log.info("秒传命中 tenant={} md5={} fileId={}", ctx.getTenantId(), md5, existing.getId());
                return existing;
            }

            // 安全要求 #5：path 永远只用 UUID，originalName 仅作为显示元数据
            String original = file.getOriginalFilename() == null
                ? "unknown.bin" : file.getOriginalFilename();
            String ext = extractExtension(original);
            String storagePath = UUID.randomUUID().toString().replace("-", "")
                + (ext == null ? "" : "." + ext);

            String bucket = properties.getMinio().getBucket();
            try (InputStream in = file.getInputStream()) {
                storage.put(bucket, storagePath, in, size,
                    contentType == null ? "application/octet-stream" : contentType);
            }

            FileMetadata meta = new FileMetadata();
            meta.setOriginalName(original);
            meta.setStoragePath(storagePath);
            meta.setBucket(bucket);
            meta.setSizeBytes(size);
            meta.setContentType(contentType);
            meta.setMd5(md5);
            meta.setSha256(sha256);
            meta.setBusinessType(businessType);
            meta.setBusinessId(businessId);
            meta.setUploader(ctx.getUserId());
            meta.setTenantId(ctx.getTenantId());
            meta.setStatus(1);
            meta.setAccessCount(0);
            metadataMapper.insert(meta);
            log.info("Uploaded file id={} tenant={} uploader={} size={}",
                meta.getId(), ctx.getTenantId(), ctx.getUserId(), size);
            return meta;
        } catch (ServiceException se) {
            throw se;
        } catch (Exception ex) {
            log.error("Upload failed", ex);
            throw new ServiceException(500, "Upload failed: " + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------
    // getMetadata — tenant-scoped, returns 404 on cross-tenant (安全 #1)
    // ---------------------------------------------------------------

    public FileMetadata getMetadata(Long id) {
        UserContext ctx = requireUserContext();
        FileMetadata m = metadataMapper.findOneInCurrentTenant(id);
        if (m == null) {
            throw new ServiceException(404, "File not found: " + id);
        }
        assertTenant(m, ctx);
        return m;
    }

    // ---------------------------------------------------------------
    // download / preview / presignedUrl
    // ---------------------------------------------------------------

    public InputStream download(Long id) {
        FileMetadata m = getMetadata(id);   // 已含 tenant check
        return storage.get(m.getBucket(), m.getStoragePath());
    }

    /**
     * Inline preview —— 调用方拿到 presigned URL 后自行展示。Service 仅负责鉴权 + URL 生成。
     */
    public String preview(Long id) {
        FileMetadata m = getMetadata(id);
        return storage.presignedUrl(m.getBucket(), m.getStoragePath(),
            Duration.ofSeconds(properties.getDefaultPresignedExpirySeconds()));
    }

    /**
     * 生成预签名下载 URL。{@code expirySeconds=null} 时使用默认值；最大 24h（安全 #8）。
     */
    public String presignedUrl(Long id, Long expirySeconds) {
        FileMetadata m = getMetadata(id);
        long max = properties.getMaxPresignedExpirySeconds();
        long def = properties.getDefaultPresignedExpirySeconds();
        long requested = expirySeconds == null || expirySeconds <= 0 ? def : expirySeconds;
        long clamped = Math.min(requested, max);
        return storage.presignedUrl(m.getBucket(), m.getStoragePath(), Duration.ofSeconds(clamped));
    }

    // ---------------------------------------------------------------
    // delete — uploader OR super_admin only (安全 #2)
    // ---------------------------------------------------------------

    @Transactional
    public void delete(Long id) {
        UserContext ctx = requireUserContext();
        // Use cross-tenant read to detect id existence at all (else we can't tell 404 from 403).
        FileMetadata m = metadataMapper.selectByIdIgnoreTenant(id);
        if (m == null) {
            throw new ServiceException(404, "File not found: " + id);
        }
        boolean isSuperAdmin = ctx.getRoles() != null && ctx.getRoles().contains(SUPER_ADMIN_ROLE);
        if (!isSuperAdmin) {
            // Non-super: same tenant AND uploader == userId
            if (!m.getTenantId().equals(ctx.getTenantId())
                || !m.getUploader().equals(ctx.getUserId())) {
                // 403, not 404, because existence is implied by delete permission semantics.
                throw new ServiceException(403, "Forbidden: not the uploader");
            }
        }
        // soft delete (BaseEntity.@TableLogic)
        metadataMapper.deleteById(id);
        // 删除 MinIO 对象 (best-effort，失败仅 log)
        try {
            storage.delete(m.getBucket(), m.getStoragePath());
        } catch (Exception ex) {
            log.warn("MinIO delete failed for id={} path={} — soft-delete already committed",
                id, m.getStoragePath(), ex);
        }
        log.info("Deleted file id={} uploader={} (super={})", id, ctx.getUserId(), isSuperAdmin);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    /** 安全 #1: 跨租户读必须 404 (避免存在性泄露)，不允许 403。 */
    private void assertTenant(FileMetadata m, UserContext ctx) {
        if (!m.getTenantId().equals(ctx.getTenantId())) {
            // 非 super_admin 一律 404；super_admin 不进入此分支
            throw new ServiceException(404, "File not found: " + m.getId());
        }
    }

    /** 安全 #4: 按 businessType 检查 contentType 白名单。businessType=null 跳过。 */
    private void assertContentTypeAllowed(String businessType, String contentType) {
        if (businessType == null || businessType.isBlank()) return;
        List<String> allow = properties.getContentTypeAllowList()
            .getOrDefault(businessType.toLowerCase(Locale.ROOT), Collections.emptyList());
        if (allow.isEmpty()) return;
        if (contentType == null) {
            throw new ServiceException(415, "Content-Type required for businessType=" + businessType);
        }
        for (String allowed : allow) {
            if (allowed.equalsIgnoreCase(contentType)) return;
        }
        throw new ServiceException(415,
            "Content-Type not allowed: " + contentType + " for " + businessType);
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > 16 || !ext.matches("[a-z0-9]+")) return null;
        return ext;
    }

    /** 安全 #11: 流式 hash，8KB buffer，永不全量加载到内存。 */
    private static Hashes hashStream(InputStream in) throws NoSuchAlgorithmException, java.io.IOException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[STREAM_BUFFER];
        int n;
        while ((n = in.read(buf)) > 0) {
            md5.update(buf, 0, n);
            sha.update(buf, 0, n);
        }
        Hashes out = new Hashes();
        out.md5 = toHex(md5.digest());
        out.sha256 = toHex(sha.digest());
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static final class Hashes {
        String md5;
        String sha256;
    }

    /** 暴露给 controller 用于快速 count/列表场景。 */
    public long countByBusiness(String businessType, String businessId) {
        UserContext ctx = requireUserContext();
        LambdaQueryWrapper<FileMetadata> w = new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getBusinessType, businessType)
            .eq(FileMetadata::getBusinessId, businessId);
        return metadataMapper.selectCount(w);
    }
}
