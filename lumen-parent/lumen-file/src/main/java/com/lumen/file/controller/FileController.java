package com.lumen.file.controller;

import com.lumen.common.core.domain.R;
import com.lumen.file.entity.FileMetadata;
import com.lumen.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件管理 controller。
 *
 * <p>路由 {@code /file/*} —— 注意 {@code /file/chunk/*} 由 {@link ChunkController}
 * 单独处理；本 controller 负责整文件上传 / 下载 / 元数据 / 预签名。</p>
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 上传文件 (multipart/form-data)。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<FileMetadata> upload(@RequestPart("file") MultipartFile file,
                                  @RequestParam(value = "businessType", required = false) String businessType,
                                  @RequestParam(value = "businessId", required = false) String businessId) {
        return R.ok(fileService.upload(file, businessType, businessId));
    }

    /** 文件元数据查询 —— 跨租户返回 404。 */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FileMetadata> metadata(@PathVariable Long id) {
        return R.ok(fileService.getMetadata(id));
    }

    /** 下载 —— attachment。 */
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        FileMetadata m = fileService.getMetadata(id);
        InputStream in = fileService.download(id);
        String fname = m.getOriginalName() == null ? ("file-" + id) : m.getOriginalName();
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        headers.add(HttpHeaders.CONTENT_TYPE,
            m.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : m.getContentType());
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(m.getSizeBytes()));
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(in));
    }

    /** Inline preview —— 返回预签名 URL。 */
    @GetMapping("/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    public R<String> preview(@PathVariable Long id) {
        return R.ok(fileService.preview(id));
    }

    /** 删除 —— 只有 uploader 或 super_admin 可删。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return R.ok();
    }

    /** 生成预签名下载 URL。{@code expiry} (秒) 可选，默认 1h，最大 24h。 */
    @GetMapping("/{id}/url")
    @PreAuthorize("isAuthenticated()")
    public R<String> presigned(@PathVariable Long id,
                               @RequestParam(value = "expiry", required = false) Long expiry) {
        return R.ok(fileService.presignedUrl(id, expiry));
    }
}
