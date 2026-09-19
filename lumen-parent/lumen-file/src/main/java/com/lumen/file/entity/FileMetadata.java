package com.lumen.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件元数据。
 * status: 1=可用 0=已删除 (soft delete handled by {@code BaseEntity.deleted}).
 *
 * <p>Tenant scoping is enforced by {@code TenantLineInnerInterceptor}; the table
 * has a {@code tenant_id} column so we MUST NOT annotate this mapper
 * {@code @InterceptorIgnore(tenantLine = "true")}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_metadata")
public class FileMetadata extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;
    private String storagePath;
    private String bucket;
    private Long sizeBytes;
    private String contentType;
    private String md5;
    private String sha256;
    private String businessType;
    private String businessId;
    private Long uploader;
    private Long tenantId;
    private Integer status;
    private Integer accessCount;
    private String accessUrl;
}
