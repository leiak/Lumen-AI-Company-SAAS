-- ============================================================
-- V3.7.0 — P4-B3.1: inventory module tables (inv_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- Module: lumen-inventory (10 services, 9 tables)
-- ============================================================

-- 1) 仓库
CREATE TABLE inv_warehouse (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    address             VARCHAR(512)    DEFAULT NULL,
    manager_id          BIGINT          DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_tenant_code (tenant_id, code, deleted),
    KEY idx_warehouse_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库主表';

-- 2) 库位
CREATE TABLE inv_location (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    warehouse_id        BIGINT          NOT NULL,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    type                VARCHAR(16)     NOT NULL DEFAULT 'storage' COMMENT 'storage/picking/receiving/shipping',
    capacity            DECIMAL(18,4)   DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_warehouse_code (tenant_id, warehouse_id, code, deleted),
    KEY idx_location_warehouse (tenant_id, warehouse_id, type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位';

-- 3) SKU 主数据
CREATE TABLE inv_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    sku                 VARCHAR(64)     DEFAULT NULL,
    category            VARCHAR(64)     DEFAULT NULL,
    unit                VARCHAR(16)     NOT NULL,
    spec                VARCHAR(255)    DEFAULT NULL,
    barcode             VARCHAR(64)     DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_tenant_code (tenant_id, code, deleted),
    UNIQUE KEY uk_item_tenant_barcode (tenant_id, barcode, deleted),
    KEY idx_item_category (tenant_id, category, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU 主数据';

-- 4) 库存 (按仓库+SKU+批次)
CREATE TABLE inv_stock (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    warehouse_id            BIGINT          NOT NULL,
    location_id             BIGINT          DEFAULT NULL,
    item_id                 BIGINT          NOT NULL,
    batch_no                VARCHAR(64)     NOT NULL,
    quantity                DECIMAL(18,4)   NOT NULL DEFAULT 0,
    available_quantity       DECIMAL(18,4)   NOT NULL DEFAULT 0,
    locked_quantity         DECIMAL(18,4)   NOT NULL DEFAULT 0,
    last_in_at              DATETIME        DEFAULT NULL,
    last_out_at             DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #12: 同一批次同位置只能一条 stock
    UNIQUE KEY uk_stock_unique_batch (warehouse_id, location_id, item_id, batch_no, deleted),
    KEY idx_stock_warehouse_item (tenant_id, warehouse_id, item_id, deleted),
    KEY idx_stock_item_last_in (tenant_id, item_id, last_in_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存记录';

-- 5) 出入库单
CREATE TABLE inv_inout (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL,
    type                    VARCHAR(16)     NOT NULL                COMMENT 'in/out/transfer',
    source_type             VARCHAR(16)     NOT NULL                COMMENT 'purchase/sales/transfer/manual',
    source_id               BIGINT          DEFAULT NULL            COMMENT 'cross-service reference; leave TODO for Feign',
    warehouse_id            BIGINT          NOT NULL,
    target_warehouse_id     BIGINT          DEFAULT NULL            COMMENT 'transfer type only',
    operator_id             BIGINT          DEFAULT NULL,
    inout_date              DATE            NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/confirmed/cancelled',
    remark                  VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inout_tenant_code (tenant_id, code, deleted),
    KEY idx_inout_type_status (tenant_id, type, status, inout_date),
    KEY idx_inout_source (source_type, source_id),
    KEY idx_inout_warehouse (tenant_id, warehouse_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库单';

-- 6) 出入库明细
CREATE TABLE inv_inout_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    inout_id            BIGINT          NOT NULL,
    item_id             BIGINT          NOT NULL,
    batch_no            VARCHAR(64)     DEFAULT NULL,
    quantity            DECIMAL(18,4)   NOT NULL,
    unit_price          DECIMAL(18,2)   DEFAULT NULL,
    subtotal            DECIMAL(18,2)   DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_inout_item_inout (tenant_id, inout_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库明细';

-- 7) 调拨单
CREATE TABLE inv_transfer (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL,
    from_warehouse_id       BIGINT          NOT NULL,
    to_warehouse_id         BIGINT          NOT NULL,
    transfer_date           DATE            NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/in_transit/received/cancelled',
    operator_id             BIGINT          DEFAULT NULL,
    remark                  VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_tenant_code (tenant_id, code, deleted),
    KEY idx_transfer_from_to (tenant_id, from_warehouse_id, to_warehouse_id, status),
    KEY idx_transfer_status (status, transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单';

-- 8) 调拨明细
CREATE TABLE inv_transfer_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    transfer_id         BIGINT          NOT NULL,
    item_id             BIGINT          NOT NULL,
    batch_no            VARCHAR(64)     DEFAULT NULL,
    quantity            DECIMAL(18,4)   NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_transfer_item_transfer (tenant_id, transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨明细';

-- 9) 盘点单
CREATE TABLE inv_stocktake (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    warehouse_id        BIGINT          NOT NULL,
    period              VARCHAR(16)     NOT NULL                COMMENT 'yyyy-MM',
    planned_at          DATETIME        NOT NULL,
    completed_at        DATETIME        DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'planning' COMMENT 'planning/in_progress/completed',
    diff_count          INT             NOT NULL DEFAULT 0,
    operator_id         BIGINT          DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_tenant_code (tenant_id, code, deleted),
    KEY idx_stocktake_warehouse_period (tenant_id, warehouse_id, period),
    KEY idx_stocktake_status (status, planned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单';

-- 10) 盘点明细
CREATE TABLE inv_stocktake_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    stocktake_id        BIGINT          NOT NULL,
    item_id             BIGINT          NOT NULL,
    warehouse_id        BIGINT          NOT NULL,
    batch_no            VARCHAR(64)     DEFAULT NULL,
    system_quantity     DECIMAL(18,4)   NOT NULL DEFAULT 0,
    actual_quantity     DECIMAL(18,4)   DEFAULT NULL,
    diff_quantity       DECIMAL(18,4)   DEFAULT NULL,
    submitted           TINYINT         NOT NULL DEFAULT 0       COMMENT '0=pending 1=submitted',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stocktake_item_stocktake (tenant_id, stocktake_id),
    KEY idx_stocktake_item_item (tenant_id, item_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点明细';

-- 11) 安全库存
CREATE TABLE inv_safety_stock (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    warehouse_id        BIGINT          NOT NULL,
    item_id             BIGINT          NOT NULL,
    min_quantity        DECIMAL(18,4)   NOT NULL DEFAULT 0,
    max_quantity        DECIMAL(18,4)   NOT NULL DEFAULT 0,
    current_quantity    DECIMAL(18,4)   NOT NULL DEFAULT 0,
    alert_status        VARCHAR(16)     NOT NULL DEFAULT 'normal' COMMENT 'normal/low/out_of_stock/overstock',
    last_alert_at       DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_safety_warehouse_item (tenant_id, warehouse_id, item_id, deleted),
    KEY idx_safety_alert (tenant_id, alert_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全库存';