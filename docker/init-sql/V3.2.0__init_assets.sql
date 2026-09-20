-- ============================================================
-- V3.2.0 — P4 assets: asset / category / custody / transfer /
--                stocktake / stocktake_item / depreciation /
--                vehicle / seal / seal_usage / certificate
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 资产卡片
CREATE TABLE ast_asset (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL,
    name                    VARCHAR(128)    NOT NULL,
    category_id             BIGINT          DEFAULT NULL,
    original_value          DECIMAL(18,2)   NOT NULL DEFAULT 0,
    current_value           DECIMAL(18,2)   NOT NULL DEFAULT 0,
    depreciation_method     VARCHAR(32)     DEFAULT NULL                COMMENT 'straight_line/double_declining/sum_of_years',
    useful_life_months      INT             DEFAULT NULL,
    salvage_value           DECIMAL(18,2)   DEFAULT 0,
    purchase_date           DATE            DEFAULT NULL,
    dept_id                 BIGINT          DEFAULT NULL,
    custodian_id            BIGINT          DEFAULT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'in_stock' COMMENT 'in_stock/in_use/maintenance/scrapped',
    qr_code                 VARCHAR(255)    DEFAULT NULL,
    image_url               VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code_deleted (tenant_id, code, deleted),
    INDEX idx_category (category_id),
    INDEX idx_dept_status (dept_id, status),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产卡片';

-- 资产分类（树形，path 物化路径便于子树查询）
CREATE TABLE ast_category (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    parent_id                   BIGINT          NOT NULL DEFAULT 0,
    code                        VARCHAR(64)     NOT NULL,
    name                        VARCHAR(128)    NOT NULL,
    level                       INT             NOT NULL DEFAULT 1,
    path                        VARCHAR(500)    NOT NULL DEFAULT '0'        COMMENT '物化路径：/1/3/7',
    depreciation_method_default VARCHAR(32)     DEFAULT NULL                COMMENT '默认折旧方法',
    useful_life_default         INT             DEFAULT NULL                COMMENT '默认使用月数',
    tenant_id                   BIGINT          NOT NULL DEFAULT 0,
    create_by                   BIGINT          DEFAULT NULL,
    create_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by                   BIGINT          DEFAULT NULL,
    update_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                     TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code_deleted (tenant_id, code, deleted),
    INDEX idx_parent (parent_id),
    INDEX idx_path (path),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产分类';

-- 资产领用
CREATE TABLE ast_custody (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id        BIGINT          NOT NULL,
    custodian_id    BIGINT          NOT NULL,
    start_at        DATETIME        NOT NULL,
    end_at          DATETIME        DEFAULT NULL,
    return_at       DATETIME        DEFAULT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'active'   COMMENT 'active/returned',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_asset (asset_id),
    INDEX idx_custodian_status (custodian_id, status),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_asset (tenant_id, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产领用';

-- 资产调拨
CREATE TABLE ast_transfer (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id            BIGINT          NOT NULL,
    from_dept_id        BIGINT          DEFAULT NULL,
    from_custodian_id   BIGINT          DEFAULT NULL,
    to_dept_id          BIGINT          NOT NULL,
    to_custodian_id     BIGINT          NOT NULL,
    transfer_date       DATE            DEFAULT NULL,
    reason              VARCHAR(512)    DEFAULT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/completed',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_asset (asset_id),
    INDEX idx_status (status),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产调拨';

-- 盘点单
CREATE TABLE ast_stocktake (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)     NOT NULL,
    period          VARCHAR(16)     NOT NULL                    COMMENT 'yyyy-MM',
    department_id   BIGINT          NOT NULL,
    planned_at      DATETIME        DEFAULT NULL,
    completed_at    DATETIME        DEFAULT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'planning' COMMENT 'planning/in_progress/completed',
    diff_count      INT             NOT NULL DEFAULT 0,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code_deleted (tenant_id, code, deleted),
    INDEX idx_period_dept (period, department_id),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单';

-- 盘点单明细
CREATE TABLE ast_stocktake_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    stocktake_id        BIGINT          NOT NULL,
    asset_id            BIGINT          NOT NULL,
    expected_location   VARCHAR(255)    DEFAULT NULL,
    actual_location     VARCHAR(255)    DEFAULT NULL,
    expected_status     VARCHAR(32)     DEFAULT NULL,
    actual_status       VARCHAR(32)     DEFAULT NULL,
    diff_type           VARCHAR(32)     NOT NULL DEFAULT 'none' COMMENT 'none/lost/extra/moved/damaged',
    note                VARCHAR(512)    DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_stocktake (stocktake_id),
    INDEX idx_asset (asset_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单明细';

-- 折旧明细（UNIQUE(asset_id, period) 保证同期间不重复计提）
CREATE TABLE ast_depreciation (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id                BIGINT          NOT NULL,
    period                  VARCHAR(16)     NOT NULL                COMMENT 'yyyy-MM',
    depreciation_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0,
    accumulated_amount      DECIMAL(18,2)   NOT NULL DEFAULT 0,
    net_value               DECIMAL(18,2)   NOT NULL DEFAULT 0,
    calculated_at           DATETIME        DEFAULT NULL,
    method                  VARCHAR(32)     DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_period (asset_id, period, deleted),
    INDEX idx_period (period),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产折旧明细';

-- 车辆扩展
CREATE TABLE ast_vehicle (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id                    BIGINT          NOT NULL,
    plate_no                    VARCHAR(32)     NOT NULL,
    vehicle_type                VARCHAR(32)     DEFAULT NULL,
    capacity                    INT             DEFAULT NULL,
    current_mileage             BIGINT          NOT NULL DEFAULT 0,
    last_maintenance_at         DATE            DEFAULT NULL,
    next_maintenance_mileage    BIGINT          DEFAULT NULL,
    tenant_id                   BIGINT          NOT NULL DEFAULT 0,
    create_by                   BIGINT          DEFAULT NULL,
    create_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by                   BIGINT          DEFAULT NULL,
    update_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                     TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_plate_deleted (tenant_id, plate_no, deleted),
    INDEX idx_asset (asset_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆扩展';

-- 印章
CREATE TABLE ast_seal (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(64)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    seal_type       VARCHAR(32)     NOT NULL                COMMENT 'company/contract/finance/legal',
    keeper_id       BIGINT          DEFAULT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'in_use' COMMENT 'in_use/sealed/destroyed',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code_deleted (tenant_id, code, deleted),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印章';

-- 印章用印记录
CREATE TABLE ast_seal_usage (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    seal_id         BIGINT          NOT NULL,
    document_name   VARCHAR(255)    NOT NULL,
    document_id     VARCHAR(128)    DEFAULT NULL,
    user_id         BIGINT          NOT NULL,
    used_at         DATETIME        NOT NULL,
    returned_at     DATETIME        DEFAULT NULL,
    witness_id      BIGINT          DEFAULT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_seal (seal_id),
    INDEX idx_user (user_id),
    INDEX idx_returned (returned_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印章用印记录';

-- 证照（营业执照/税务登记/ISO资质/其他）
CREATE TABLE ast_certificate (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    certificate_type    VARCHAR(32)     NOT NULL                COMMENT 'business_license/tax_registration/iso_qualification/other',
    certificate_no      VARCHAR(64)     NOT NULL,
    holder              VARCHAR(128)    NOT NULL,
    issue_date          DATE            DEFAULT NULL,
    expire_at           DATE            DEFAULT NULL,
    file_url            VARCHAR(512)    DEFAULT NULL,
    status              VARCHAR(32)     DEFAULT NULL            COMMENT 'active/normal/warning/critical/expired',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_no_deleted (tenant_id, certificate_no, deleted),
    INDEX idx_type (certificate_type),
    INDEX idx_expire (expire_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证照';