-- ============================================================
-- V3.3.0 — P4: contract management tables (ctr_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 合同主表
CREATE TABLE ctr_contract (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_no             VARCHAR(64)     NOT NULL                COMMENT '合同编号',
    title                   VARCHAR(255)    NOT NULL                COMMENT '合同标题',
    type                    VARCHAR(16)     NOT NULL                COMMENT 'sales/purchase/lease/service/employment/other',
    party_a                 VARCHAR(128)    DEFAULT NULL            COMMENT '甲方',
    party_b                 VARCHAR(128)    DEFAULT NULL            COMMENT '乙方',
    party_a_signed_at       DATETIME        DEFAULT NULL,
    party_b_signed_at       DATETIME        DEFAULT NULL,
    amount                  DECIMAL(18,2)   DEFAULT NULL,
    currency                VARCHAR(8)      DEFAULT 'CNY',
    start_date              DATE            DEFAULT NULL,
    end_date                DATE            DEFAULT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'drafting'
        COMMENT 'drafting/pending_approval/approved/signing/signed/fulfilling/expired/terminated/archived',
    template_id             BIGINT          DEFAULT NULL,
    drafter_id              BIGINT          NOT NULL,
    workflow_instance_id    BIGINT          DEFAULT NULL,
    file_id                 BIGINT          DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_contract_no (tenant_id, contract_no, deleted),
    INDEX idx_status (status, tenant_id),
    INDEX idx_drafter (drafter_id, status),
    INDEX idx_end_date (end_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同主表';

-- 合同条款模板
CREATE TABLE ctr_clause_template (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(128)    NOT NULL,
    type                    VARCHAR(32)     DEFAULT NULL,
    variables               JSON            DEFAULT NULL            COMMENT '变量名数组 ["name","amount"]',
    status                  TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=禁用',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同条款模板';

-- 合同条款
CREATE TABLE ctr_clause (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    clause_no               VARCHAR(32)     DEFAULT NULL,
    title                   VARCHAR(255)    DEFAULT NULL,
    content                 LONGTEXT        DEFAULT NULL,
    order_num               INT             NOT NULL DEFAULT 0,
    source                  VARCHAR(16)     NOT NULL DEFAULT 'manual' COMMENT 'template/manual',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同条款';

-- 收款/付款计划
CREATE TABLE ctr_payment_plan (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    plan_no                 VARCHAR(32)     DEFAULT NULL,
    planned_amount          DECIMAL(18,2)   NOT NULL,
    planned_date            DATE            NOT NULL,
    actual_amount           DECIMAL(18,2)   DEFAULT NULL,
    actual_date             DATE            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/completed/overdue',
    payment_id              BIGINT          DEFAULT NULL            COMMENT '关联财务付款单',
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, planned_date),
    INDEX idx_status_due (status, planned_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款/付款计划';

-- 履约里程碑
CREATE TABLE ctr_fulfillment (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    milestone_name          VARCHAR(255)    NOT NULL,
    planned_date            DATE            NOT NULL,
    completed_date          DATE            DEFAULT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/completed/overdue',
    evidence_file_id        BIGINT          DEFAULT NULL,
    note                    VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, planned_date),
    INDEX idx_status (status, planned_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='履约里程碑';

-- 合同附件
CREATE TABLE ctr_attachment (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    file_id                 BIGINT          NOT NULL,
    attachment_type         VARCHAR(32)     NOT NULL DEFAULT 'other' COMMENT 'main_contract/supplementary/invoice/other',
    uploaded_at             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, attachment_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同附件';

-- 合同变更日志
CREATE TABLE ctr_change_log (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    change_type             VARCHAR(32)     NOT NULL                COMMENT 'draft/approve/sign/payment/terminate/other',
    before_value            JSON            DEFAULT NULL,
    after_value             JSON            DEFAULT NULL,
    operator_id             BIGINT          NOT NULL,
    operated_at             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    comment                 VARCHAR(512)    DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更日志';

-- 签署任务
CREATE TABLE ctr_sign_task (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    contract_id             BIGINT          NOT NULL,
    signer_user_id          BIGINT          NOT NULL,
    signer_role             VARCHAR(16)     NOT NULL                COMMENT 'party_a/party_b/witness/internal',
    sign_method             VARCHAR(16)     NOT NULL                COMMENT 'electronic/wet/witness',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/signed/rejected/expired',
    sign_provider           VARCHAR(32)     DEFAULT NULL            COMMENT 'qiyuesuo/fadada/esign',
    external_task_id        VARCHAR(128)    DEFAULT NULL,
    signed_at               DATETIME        DEFAULT NULL,
    expire_at               DATETIME        DEFAULT NULL,
    file_id                 BIGINT          DEFAULT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    create_by               BIGINT          DEFAULT NULL,
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by               BIGINT          DEFAULT NULL,
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_contract (contract_id, status),
    INDEX idx_signer (signer_user_id, status),
    INDEX idx_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签署任务';
