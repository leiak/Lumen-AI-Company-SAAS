-- ============================================================
-- V3.5.0 — P4-B2.2: sales & CRM tables (sal_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- ------------------------------------------------------------
-- 客户表 — 公海/私海
-- ------------------------------------------------------------
CREATE TABLE sal_customer (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL                COMMENT '客户编号,业务唯一',
    name                    VARCHAR(255)    NOT NULL                COMMENT '客户名称',
    level                   VARCHAR(8)      DEFAULT NULL            COMMENT 'A/B/C/D 1-4',
    source                  VARCHAR(32)     DEFAULT NULL            COMMENT 'referral/ad/website/direct/event/other',
    owner_user_id           BIGINT          DEFAULT NULL            COMMENT '私海 owner,公海为 NULL',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'in_pool'
        COMMENT 'in_pool/private/active/lost',
    industry                VARCHAR(64)     DEFAULT NULL,
    scale                   VARCHAR(16)     DEFAULT NULL            COMMENT 'small/medium/large/enterprise',
    address                 VARCHAR(512)    DEFAULT NULL,
    last_contact_at         DATETIME        DEFAULT NULL,
    lost_reason             VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code, deleted),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_owner (owner_user_id, status),
    INDEX idx_last_contact (last_contact_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户(公海/私海)';

-- ------------------------------------------------------------
-- 联系人 — mobile/email 加密
-- ------------------------------------------------------------
CREATE TABLE sal_contact (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    customer_id             BIGINT          NOT NULL,
    name                    VARCHAR(64)     NOT NULL,
    position                VARCHAR(64)     DEFAULT NULL,
    mobile_enc              VARCHAR(512)    DEFAULT NULL            COMMENT 'AES-GCM 加密',
    email_enc               VARCHAR(512)    DEFAULT NULL            COMMENT 'AES-GCM 加密',
    phone                   VARCHAR(32)     DEFAULT NULL,
    is_primary              TINYINT         NOT NULL DEFAULT 0,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_customer (customer_id, is_primary),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人(mobile/email 加密)';

-- ------------------------------------------------------------
-- 销售线索 Lead
-- ------------------------------------------------------------
CREATE TABLE sal_lead (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    customer_name           VARCHAR(255)    NOT NULL,
    contact_name            VARCHAR(64)     DEFAULT NULL,
    mobile_enc              VARCHAR(512)    DEFAULT NULL            COMMENT 'AES-GCM 加密',
    source                  VARCHAR(32)     DEFAULT NULL            COMMENT 'referral/ad/website/direct/event/other',
    requirement             TEXT            DEFAULT NULL,
    estimated_value         DECIMAL(18,2)   DEFAULT NULL,
    owner_user_id           BIGINT          NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'new'
        COMMENT 'new/contacting/qualified/lost/converted',
    converted_customer_id   BIGINT          DEFAULT NULL            COMMENT 'cross-ref sal_customer.id',
    last_followup_at        DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_owner_status (owner_user_id, status),
    INDEX idx_source (source, status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售线索';

-- ------------------------------------------------------------
-- 销售机会 Opportunity
-- ------------------------------------------------------------
CREATE TABLE sal_opportunity (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(255)    NOT NULL,
    customer_id             BIGINT          NOT NULL                COMMENT 'cross-ref sal_customer.id',
    lead_id                 BIGINT          DEFAULT NULL            COMMENT 'cross-ref sal_lead.id',
    stage                   VARCHAR(32)     NOT NULL DEFAULT 'qualification'
        COMMENT 'qualification/proposal/negotiation/won/lost',
    amount                  DECIMAL(18,2)   NOT NULL DEFAULT 0,
    probability             TINYINT         DEFAULT NULL            COMMENT '0-100',
    expected_close_date     DATE            NOT NULL,
    owner_user_id           BIGINT          NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'open' COMMENT 'open/won/lost',
    close_reason            VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_customer (customer_id, stage),
    INDEX idx_owner_stage (owner_user_id, stage),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_expected_close (expected_close_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售机会';

-- ------------------------------------------------------------
-- 报价单 Quotation (多版本 — code + opportunity_id 复合 UNIQUE)
-- ------------------------------------------------------------
CREATE TABLE sal_quotation (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL                COMMENT '报价单编号',
    opportunity_id          BIGINT          NOT NULL                COMMENT 'cross-ref sal_opportunity.id',
    version                 INT             NOT NULL DEFAULT 1,
    total_amount            DECIMAL(18,2)   NOT NULL DEFAULT 0,
    valid_until             DATE            DEFAULT NULL,
    terms                   TEXT            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft'
        COMMENT 'draft/sent/accepted/rejected/expired',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code, deleted),
    INDEX idx_opportunity (opportunity_id, version DESC),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单(多版本)';

-- ------------------------------------------------------------
-- 报价单明细
-- ------------------------------------------------------------
CREATE TABLE sal_quotation_item (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    quotation_id            BIGINT          NOT NULL                COMMENT 'cross-ref sal_quotation.id',
    item_name               VARCHAR(255)    NOT NULL,
    sku                     VARCHAR(64)     DEFAULT NULL,
    quantity                INT             NOT NULL DEFAULT 1,
    unit_price              DECIMAL(18,2)   NOT NULL DEFAULT 0,
    subtotal                DECIMAL(18,2)   NOT NULL DEFAULT 0,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_quotation (quotation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单明细';

-- ------------------------------------------------------------
-- 订单 Order
-- ------------------------------------------------------------
CREATE TABLE sal_order (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL                COMMENT '订单编号',
    customer_id             BIGINT          NOT NULL                COMMENT 'cross-ref sal_customer.id',
    contract_id             BIGINT          DEFAULT NULL            COMMENT 'cross-ref ctr_contract.id (TODO P5 Feign)',
    source_type             VARCHAR(16)     NOT NULL DEFAULT 'direct' COMMENT 'opportunity/quotation/direct',
    source_id               BIGINT          DEFAULT NULL            COMMENT 'cross-ref sal_opportunity.id 或 sal_quotation.id',
    total_amount            DECIMAL(18,2)   NOT NULL DEFAULT 0,
    order_date              DATE            NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft'
        COMMENT 'draft/confirmed/shipping/shipped/completed/cancelled',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code, deleted),
    INDEX idx_customer_status (customer_id, status),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_contract (contract_id),
    INDEX idx_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单';

-- ------------------------------------------------------------
-- 订单明细
-- ------------------------------------------------------------
CREATE TABLE sal_order_item (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    order_id                BIGINT          NOT NULL                COMMENT 'cross-ref sal_order.id',
    item_name               VARCHAR(255)    NOT NULL,
    sku                     VARCHAR(64)     DEFAULT NULL,
    quantity                INT             NOT NULL DEFAULT 1,
    unit_price              DECIMAL(18,2)   NOT NULL DEFAULT 0,
    subtotal                DECIMAL(18,2)   NOT NULL DEFAULT 0,
    shipped_quantity        INT             NOT NULL DEFAULT 0,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_order (order_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- ------------------------------------------------------------
-- 发货单 Shipment
-- ------------------------------------------------------------
CREATE TABLE sal_shipment (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL                COMMENT '发货单号',
    order_id                BIGINT          NOT NULL                COMMENT 'cross-ref sal_order.id',
    shipment_date           DATE            DEFAULT NULL,
    carrier                 VARCHAR(64)     DEFAULT NULL            COMMENT '物流公司',
    tracking_no             VARCHAR(64)     DEFAULT NULL            COMMENT '运单号',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'pending'
        COMMENT 'pending/in_transit/delivered/exception',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code, deleted),
    INDEX idx_order (order_id, status),
    INDEX idx_carrier_status (carrier, status),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单';

-- ------------------------------------------------------------
-- 应收账款 Receivable
-- ------------------------------------------------------------
CREATE TABLE sal_receivable (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(64)     NOT NULL                COMMENT '应收单号',
    order_id                BIGINT          NOT NULL                COMMENT 'cross-ref sal_order.id',
    customer_id             BIGINT          NOT NULL                COMMENT 'cross-ref sal_customer.id',
    amount                  DECIMAL(18,2)   NOT NULL DEFAULT 0,
    due_date                DATE            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'pending'
        COMMENT 'pending/partial/collected/overdue',
    collected_amount        DECIMAL(18,2)   NOT NULL DEFAULT 0,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code, deleted),
    INDEX idx_customer_status (customer_id, status),
    INDEX idx_status_due (status, due_date),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收账款';

-- ------------------------------------------------------------
-- 回款记录 PaymentRecord
-- ------------------------------------------------------------
CREATE TABLE sal_payment_record (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    receivable_id           BIGINT          NOT NULL                COMMENT 'cross-ref sal_receivable.id',
    amount                  DECIMAL(18,2)   NOT NULL DEFAULT 0,
    payment_method          VARCHAR(32)     NOT NULL                COMMENT 'cash/bank/alipay/wechat/other',
    paid_at                 DATETIME        NOT NULL,
    operator_id             BIGINT          NOT NULL,
    remark                  VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_receivable (receivable_id, paid_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款记录';

-- ------------------------------------------------------------
-- 对账单 Statement — UNIQUE(customer_id, period_start, period_end, deleted)
-- ------------------------------------------------------------
CREATE TABLE sal_statement (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    customer_id             BIGINT          NOT NULL                COMMENT 'cross-ref sal_customer.id',
    period_start            DATE            NOT NULL,
    period_end              DATE            NOT NULL,
    total_amount            DECIMAL(18,2)   NOT NULL DEFAULT 0,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/sent/confirmed',
    generated_at            DATETIME        DEFAULT CURRENT_TIMESTAMP,
    sent_at                 DATETIME        DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_period (tenant_id, customer_id, period_start, period_end, deleted),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_customer_period (customer_id, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账单';