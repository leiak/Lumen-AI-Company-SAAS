-- ============================================================
-- V3.4.0 — P4-B2.1: procurement module tables (proc_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 供应商主表
CREATE TABLE proc_supplier (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    tax_no              VARCHAR(64)     DEFAULT NULL,
    level               TINYINT         NOT NULL DEFAULT 1       COMMENT '1-5',
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'active/blacklist/pending',
    contact_name        VARCHAR(64)     DEFAULT NULL,
    contact_phone_enc   VARCHAR(500)    DEFAULT NULL             COMMENT 'AES-256-GCM',
    contact_email_enc   VARCHAR(500)    DEFAULT NULL             COMMENT 'AES-256-GCM',
    address             VARCHAR(512)    DEFAULT NULL,
    rating              DECIMAL(3,2)    DEFAULT 0.00             COMMENT '0-5',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_tenant_code (tenant_id, code, deleted),
    KEY idx_supplier_status (tenant_id, status, deleted),
    KEY idx_supplier_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商主表';

-- 供应商资质附件
CREATE TABLE proc_supplier_qualification (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    supplier_id         BIGINT          NOT NULL,
    qualification_type  VARCHAR(32)     NOT NULL                COMMENT 'business_license/tax_registration/iso/other',
    file_id             BIGINT          DEFAULT NULL,
    expire_at           DATE            DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/expired',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_qual_supplier (tenant_id, supplier_id, status, deleted),
    KEY idx_qual_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商资质';

-- 询价单
CREATE TABLE proc_inquiry (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    title               VARCHAR(255)    NOT NULL,
    inquiry_date        DATE            NOT NULL,
    deadline            DATE            NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/closed/awarded',
    creator_id          BIGINT          DEFAULT NULL,
    published_at        DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inquiry_tenant_code (tenant_id, code, deleted),
    KEY idx_inquiry_status (tenant_id, status, deadline),
    KEY idx_inquiry_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价单';

-- 报价单
CREATE TABLE proc_quotation (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    inquiry_id          BIGINT          NOT NULL,
    supplier_id         BIGINT          NOT NULL,
    total_amount        DECIMAL(18,2)   NOT NULL,
    valid_until         DATE            DEFAULT NULL,
    lead_time_days      INT             NOT NULL DEFAULT 0,
    payment_terms       VARCHAR(255)    DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'submitted' COMMENT 'submitted/selected/rejected/withdrawn',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_inquiry_supplier (tenant_id, inquiry_id, supplier_id, deleted),
    KEY idx_quotation_status (status),
    KEY idx_quotation_supplier (tenant_id, supplier_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单';

-- 招投标
CREATE TABLE proc_bidding (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    title               VARCHAR(255)    NOT NULL,
    type                VARCHAR(16)     NOT NULL DEFAULT 'public' COMMENT 'public/invited',
    start_at            DATETIME        NOT NULL,
    end_at              DATETIME        NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/evaluating/awarded/closed',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bidding_tenant_code (tenant_id, code, deleted),
    KEY idx_bidding_status (tenant_id, status, end_at),
    KEY idx_bidding_window (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招投标';

-- 招投标参与方
CREATE TABLE proc_bidding_participant (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    bidding_id          BIGINT          NOT NULL,
    supplier_id         BIGINT          NOT NULL,
    bid_amount          DECIMAL(18,2)   DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'invited' COMMENT 'invited/joined/withdrew/rejected',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_bidding_supplier (tenant_id, bidding_id, supplier_id, deleted),
    KEY idx_participant_status (bidding_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招投标参与方';

-- 采购单
CREATE TABLE proc_order (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL,
    supplier_id             BIGINT          NOT NULL,
    source_type             VARCHAR(16)     NOT NULL                COMMENT 'quotation/bidding/direct',
    source_id               BIGINT          DEFAULT NULL,
    total_amount            DECIMAL(18,2)   NOT NULL DEFAULT 0,
    order_date              DATE            NOT NULL,
    expected_delivery_at    DATE            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/submitted/approved/rejected/fulfilled/cancelled',
    approver_id             BIGINT          DEFAULT NULL,
    approved_at             DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_tenant_code (tenant_id, code, deleted),
    KEY idx_order_supplier (tenant_id, supplier_id, status),
    KEY idx_order_status (status, order_date),
    KEY idx_order_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单';

-- 采购单明细
CREATE TABLE proc_order_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    order_id            BIGINT          NOT NULL,
    item_name           VARCHAR(255)    NOT NULL,
    sku                 VARCHAR(64)     DEFAULT NULL,
    quantity            INT             NOT NULL,
    unit_price          DECIMAL(18,2)   NOT NULL,
    subtotal            DECIMAL(18,2)   NOT NULL,
    received_quantity   INT             NOT NULL DEFAULT 0,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_item_order (tenant_id, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细';

-- 收货单
CREATE TABLE proc_receipt (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    order_id            BIGINT          NOT NULL,
    receipt_date        DATE            NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/discrepancy',
    inspector_id        BIGINT          DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_tenant_code (tenant_id, code, deleted),
    KEY idx_receipt_order (tenant_id, order_id, status),
    KEY idx_receipt_status (status, receipt_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

-- 付款单
CREATE TABLE proc_payment (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    payment_no          VARCHAR(64)     NOT NULL,
    source_type         VARCHAR(16)     NOT NULL                COMMENT 'order/receipt',
    source_id           BIGINT          NOT NULL,
    payable_id          BIGINT          DEFAULT NULL            COMMENT 'cross-service reference fin_payable.id',
    amount              DECIMAL(18,2)   NOT NULL,
    payment_method      VARCHAR(16)     NOT NULL                COMMENT 'cash/bank_transfer/check/other',
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/paid/rejected',
    requester_id        BIGINT          DEFAULT NULL,
    approver_id         BIGINT          DEFAULT NULL,
    paid_at             DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_tenant_no (tenant_id, payment_no, deleted),
    KEY idx_payment_source (tenant_id, source_type, source_id),
    KEY idx_payment_status (status),
    KEY idx_payment_payable (payable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单';