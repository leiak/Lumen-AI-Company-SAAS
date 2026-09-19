package com.lumen.file.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.file.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * File metadata mapper. The {@code file_metadata} table carries a
 * {@code tenant_id} column, so we deliberately let the
 * {@code TenantLineInnerInterceptor} apply its row filter on every query.
 *
 * <p>Custom methods that need cross-tenant behavior (or that bypass the
 * tenant filter intentionally) opt out via
 * {@link InterceptorIgnore @InterceptorIgnore(tenantLine = "true")}.</p>
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    /**
     * Tenant-scoped duplicate detection by md5. Cross-tenant dedupe is
     * explicitly NOT supported — see spec §业务规则 / 安全要求 #6.
     */
    @Select("SELECT * FROM file_metadata "
        + "WHERE md5 = #{md5} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC LIMIT 1")
    FileMetadata selectByMd5AndTenant(@Param("md5") String md5,
                                       @Param("tenantId") Long tenantId);

    default FileMetadata selectByMd5InTenant(String md5, Long tenantId) {
        return selectByMd5AndTenant(md5, tenantId);
    }

    /**
     * Bypasses the tenant filter — for {@link com.lumen.file.service.FileService#delete(Long)}
     * where the caller has already been authorized by uploader / super-admin check.
     * Defense in depth: the service layer still validates tenant equality.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_metadata WHERE id = #{id} AND deleted = 0 LIMIT 1")
    FileMetadata selectByIdIgnoreTenant(@Param("id") Long id);

    default FileMetadata findOneInCurrentTenant(Long id) {
        // TenantLineInnerInterceptor will rewrite the WHERE to include tenant_id.
        return selectById(id);
    }

    default long softDeleteById(Long id) {
        // deleteById honors @TableLogic on BaseEntity.deleted and returns affected rows.
        return Math.max(deleteById(id), 0);
    }

    /** Lambda helper for ad-hoc queries (e.g. count by md5 within tenant). */
    default LambdaQueryWrapper<FileMetadata> tenantWrapper() {
        return new LambdaQueryWrapper<>();
    }
}
