package com.lumen.file.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.file.entity.FileChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * File chunk mapper.
 *
 * <p>The {@code file_chunk} table has NO {@code tenant_id} column, so every
 * BaseMapper operation would fail under {@code TenantLineInnerInterceptor}
 * (which rewrites WHERE with {@code tenant_id = ?}). We bypass the tenant
 * interceptor wholesale; the service layer enforces uploader equality against
 * {@code UserContextHolder.getUserId()} on every operation.</p>
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunk> {

    /** All chunks (across all uploaders) for a given uploadId — service must filter by uploader. */
    @Select("SELECT * FROM file_chunk WHERE upload_id = #{uploadId} AND deleted = 0 ORDER BY chunk_number ASC")
    List<FileChunk> listByUploadId(@Param("uploadId") String uploadId);

    @Select("SELECT * FROM file_chunk WHERE upload_id = #{uploadId} AND chunk_number = #{chunkNumber} "
        + "AND deleted = 0 LIMIT 1")
    FileChunk selectByUploadIdAndNumber(@Param("uploadId") String uploadId,
                                        @Param("chunkNumber") Integer chunkNumber);

    @Select("SELECT COUNT(*) FROM file_chunk WHERE upload_id = #{uploadId} AND uploaded = 1 AND deleted = 0")
    int countUploaded(@Param("uploadId") String uploadId);
}
