-- ============================================================
-- V2.1.0 — P3: file storage tables (file_metadata / file_chunk)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 文件元数据
CREATE TABLE file_metadata (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    original_name   VARCHAR(255)    NOT NULL,
    storage_path    VARCHAR(512)    NOT NULL                COMMENT '对象存储 key',
    bucket          VARCHAR(64)     NOT NULL,
    size_bytes      BIGINT          NOT NULL,
    content_type    VARCHAR(128)    DEFAULT NULL,
    md5             VARCHAR(32)     DEFAULT NULL,
    sha256          VARCHAR(64)     DEFAULT NULL,
    business_type   VARCHAR(32)     DEFAULT NULL            COMMENT 'avatar/contract/expense/...',
    business_id     VARCHAR(128)    DEFAULT NULL,
    uploader        BIGINT          NOT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '1=可用 0=已删除',
    access_count    INT             NOT NULL DEFAULT 0,
    access_url      VARCHAR(1024)   DEFAULT NULL,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_business (business_type, business_id),
    INDEX idx_uploader (uploader, create_time),
    INDEX idx_md5 (md5),
    INDEX idx_sha256 (sha256),
    INDEX idx_tenant_md5 (tenant_id, md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据';

-- 分片上传
CREATE TABLE file_chunk (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    upload_id       VARCHAR(64)     NOT NULL,
    file_md5        VARCHAR(32)     NOT NULL,
    chunk_number    INT             NOT NULL,
    chunk_size      INT             NOT NULL DEFAULT 0,
    chunk_md5       VARCHAR(32)     DEFAULT NULL,
    storage_path    VARCHAR(512)    DEFAULT NULL,
    uploaded        TINYINT         NOT NULL DEFAULT 0      COMMENT '0=未完成 1=已上传',
    uploader        BIGINT          NOT NULL,
    total_chunks    INT             NOT NULL,
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号 (@Version)',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_upload_chunk (upload_id, chunk_number, deleted),
    INDEX idx_upload (upload_id, uploader)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传';
