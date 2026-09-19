-- ============================================================
-- V3.1.0 — P4-B1: finance module tables (fin_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- Module: lumen-finance (10 services, 11 tables)
-- ============================================================

-- 1) 会计科目（树形 + 物化路径）
CREATE TABLE fin_account_subject (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    parent_id           BIGINT          NOT NULL DEFAULT 0,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(128)    NOT NULL,
    level               INT             NOT NULL DEFAULT 1,
    type                VARCHAR(16)     NOT NULL                COMMENT 'asset/liability/equity/income/expense',
    balance_direction   VARCHAR(8)      NOT NULL                COMMENT 'debit/credit',
    path                VARCHAR(512)    NOT NULL DEFAULT '0'    COMMENT 'materialised path: 0,1,3,12',
    status              TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_code_tenant (code, tenant_id, deleted),
    INDEX idx_subject_path (tenant_id, path),
    INDEX idx_subject_parent (tenant_id, parent_id),
    INDEX idx_subject_type (tenant_id, type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目';

-- 2) 凭证
CREATE TABLE fin_voucher (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    voucher_no          VARCHAR(64)     NOT NULL,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    voucher_date        DATE            NOT NULL,
    summary             VARCHAR(255)    DEFAULT NULL,
    total_debit         DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    total_credit        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/posted/reversed',
    posted_at           DATETIME        DEFAULT NULL,
    posted_by           BIGINT          DEFAULT NULL,
    reversed_id         BIGINT          DEFAULT NULL            COMMENT '指向反向凭证 (双向)',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_voucher_no_tenant (voucher_no, tenant_id, deleted),
    INDEX idx_voucher_period (tenant_id, period, deleted),
    INDEX idx_voucher_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证';

-- 3) 凭证明细
CREATE TABLE fin_voucher_entry (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    voucher_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    debit_amount        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    credit_amount       DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    summary             VARCHAR(255)    DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_entry_voucher (voucher_id, deleted),
    INDEX idx_entry_subject (tenant_id, subject_id, deleted),
    -- TODO: 借贷平衡约束触发器 (应用层 VoucherService.post 已校验; DDL 触发器 deferred 到 P5)
    CONSTRAINT chk_entry_xor CHECK (NOT (debit_amount > 0 AND credit_amount > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证明细';

-- 4) 应收单
CREATE TABLE fin_receivable (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    customer_id         BIGINT          NOT NULL,
    source_type         VARCHAR(16)     NOT NULL                COMMENT 'sales/other',
    source_id           BIGINT          DEFAULT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    collected_amount    DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    due_date            DATE            DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/collected',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_recv_customer (tenant_id, customer_id, status, deleted),
    INDEX idx_recv_due (tenant_id, due_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收单';

-- 5) 应付单
CREATE TABLE fin_payable (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    supplier_id         BIGINT          NOT NULL,
    source_type         VARCHAR(16)     NOT NULL                COMMENT 'purchase/other',
    source_id           BIGINT          DEFAULT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    paid_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    due_date            DATE            DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/paid',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_pay_supplier (tenant_id, supplier_id, status, deleted),
    INDEX idx_pay_due (tenant_id, due_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付单';

-- 6) 报销单
CREATE TABLE fin_expense_report (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    applicant_id        BIGINT          NOT NULL,
    department_id       BIGINT          NOT NULL,
    total_amount        DECIMAL(18, 2)  NOT NULL,
    items               JSON            DEFAULT NULL            COMMENT '报销明细 {subjectId, amount, summary}',
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/submitted/approved/rejected/paid',
    workflow_instance_id BIGINT         DEFAULT NULL,
    submitted_at        DATETIME        DEFAULT NULL,
    approved_at         DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_exp_applicant (tenant_id, applicant_id, status, deleted),
    INDEX idx_exp_dept (tenant_id, department_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销单';

-- 7) 付款单
CREATE TABLE fin_payment (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    payment_no          VARCHAR(64)     NOT NULL,
    source_type         VARCHAR(16)     NOT NULL                COMMENT 'payable/expense',
    source_id           BIGINT          NOT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    payee               VARCHAR(128)    DEFAULT NULL,
    payment_method      VARCHAR(32)     DEFAULT NULL,
    paid_at             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    status              VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/done/failed',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no_tenant (payment_no, tenant_id, deleted),
    INDEX idx_payment_source (tenant_id, source_type, source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单';

-- 8) 预算
CREATE TABLE fin_budget (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    department_id       BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    planned_amount      DECIMAL(18, 2)  NOT NULL,
    used_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'open' COMMENT 'open/closed',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_period_dept_subject (tenant_id, period, department_id, subject_id, deleted),
    INDEX idx_budget_period (tenant_id, period, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算';

-- 9) 预算明细
CREATE TABLE fin_budget_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    budget_id           BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    planned_amount      DECIMAL(18, 2)  NOT NULL,
    used_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_budget_item_budget (budget_id, deleted),
    UNIQUE KEY uk_budget_item (budget_id, subject_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算明细';

-- 10) 发票（敏感字段加密存储）
CREATE TABLE fin_invoice (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    invoice_no          VARCHAR(64)     NOT NULL,
    invoice_type        VARCHAR(32)     NOT NULL                COMMENT 'vat_special/vat_general/electronic',
    amount              DECIMAL(18, 2)  NOT NULL,
    tax_amount          DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    issue_date          DATE            NOT NULL,
    source_type         VARCHAR(16)     DEFAULT NULL,
    source_id           BIGINT          DEFAULT NULL,
    recognize_status    VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/recognized/certified',
    buyer_name_enc      VARBINARY(2048) DEFAULT NULL            COMMENT 'AES-GCM 密文',
    seller_name_enc     VARBINARY(2048) DEFAULT NULL,
    tax_no_enc          VARBINARY(2048) DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_no_tenant (invoice_no, tenant_id, deleted),
    INDEX idx_invoice_period (tenant_id, issue_date, deleted),
    INDEX idx_invoice_status (tenant_id, recognize_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票';

-- 11) 会计期间
CREATE TABLE fin_period (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    year                INT             NOT NULL,
    month               INT             NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'open' COMMENT 'open/closed/locked',
    closed_at           DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #10: 期间唯一
    UNIQUE KEY uk_period_tenant_year_month (tenant_id, year, month, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计期间';

-- ============================================================
-- TODO P5 (comments, not yet applied):
-- 1) fin_voucher 总借贷平衡 BEFORE INSERT/UPDATE 触发器
-- 2) fin_invoice.tax_no_enc 列密文索引 (HMAC 或 BLAKE3 列) 支持按税号检索
-- 3) fin_payment.payment_no 全局序列号 (common_seq)
-- ============================================================
