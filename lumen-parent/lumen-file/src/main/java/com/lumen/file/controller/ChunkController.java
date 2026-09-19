package com.lumen.file.controller;

import com.lumen.common.core.domain.R;
import com.lumen.file.dto.CompleteChunkRequest;
import com.lumen.file.dto.InitChunkRequest;
import com.lumen.file.dto.InitChunkResponse;
import com.lumen.file.entity.FileMetadata;
import com.lumen.file.service.ChunkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传 controller。所有端点都显式 {@code @PreAuthorize("isAuthenticated()")}。
 */
@RestController
@RequestMapping("/file/chunk")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    /** 初始化分片上传会话 —— 返回 uploadId + 每片的预签名 PUT URL。 */
    @PostMapping(value = "/init", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<InitChunkResponse> init(@Valid @RequestBody InitChunkRequest req) {
        return R.ok(chunkService.init(req));
    }

    /** 上传单个分片。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<Void> uploadPart(@RequestParam("uploadId") String uploadId,
                              @RequestParam("chunkNumber") int chunkNumber,
                              @RequestPart("file") MultipartFile file) {
        chunkService.uploadPart(uploadId, chunkNumber, file);
        return R.ok();
    }

    /** 完成分片上传 —— 校验全部分片齐全并写 file_metadata。 */
    @PostMapping(value = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<FileMetadata> complete(@Valid @RequestBody CompleteChunkRequest req) {
        return R.ok(chunkService.complete(req));
    }

    /** 中止上传 —— 删除所有 part + soft-delete 行。 */
    @PostMapping(value = "/abort")
    @PreAuthorize("isAuthenticated()")
    public R<Void> abort(@RequestParam("uploadId") String uploadId) {
        chunkService.abort(uploadId);
        return R.ok();
    }
}
