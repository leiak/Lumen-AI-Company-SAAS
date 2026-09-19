package com.lumen.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分片上传记录。
 *
 * <p>Note: this table has NO {@code tenant_id} column — chunk rows are owned by
 * an upload session, which is itself scoped to a uploader and validated against
 * {@code UserContextHolder.getUserId()} on every write. The mapper MUST use
 * {@code @InterceptorIgnore(tenantLine = "true")} to bypass the multi-tenant
 * interceptor.</p>
 *
 * <p>{@code @Version} is used for optimistic locking so concurrent
 * {@code uploadPart} calls on the same chunk number don't trample each other.</p>
 */
@Data
@TableName("file_chunk")
public class FileChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uploadId;
    private String fileMd5;
    private Integer chunkNumber;
    private Integer chunkSize;
    private String chunkMd5;
    private String storagePath;

    /** 0=未完成 1=已上传 */
    private Integer uploaded;

    private Long uploader;
    private Integer totalChunks;

    /** 与 {@code BaseEntity.createTime} 对齐，字段名相同以便复用 sql。 */
    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 与 BaseEntity 不同: 这里 deleted 直接用 int 字段，不做字段填充 (与表结构一致)。
     * {@code file_chunk.deleted} 是普通字段，unique key 依赖它。
     */
    private Integer deleted;

    @Version
    private Integer version;
}
