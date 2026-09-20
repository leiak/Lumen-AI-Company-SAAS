-- ============================================================
-- V3.6.0 — P4-B2.3: payroll module tables (pay_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- Module: lumen-payroll (8 services, 8 tables)
-- ============================================================

-- 1) 薪资结构模板
CREATE TABLE pay_salary_structure (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(128)    NOT NULL,
    components          JSON            DEFAULT NULL            COMMENT 'baseSalary + allowance 项',
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_structure_code_tenant (code, tenant_id, deleted),
    INDEX idx_structure_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薪资结构模板';

-- 2) 员工薪资档案 (历史)
CREATE TABLE pay_employee_salary (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT          NOT NULL,
    structure_id        BIGINT          NOT NULL,
    base_salary         DECIMAL(18, 2)  NOT NULL,
    effective_from      DATE            NOT NULL,
    effective_to        DATE            DEFAULT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive/suspended',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #7: UNIQUE(employee_id, effective_from, deleted)
    UNIQUE KEY uk_emp_salary_unique (employee_id, effective_from, deleted),
    INDEX idx_emp_salary_emp (tenant_id, employee_id, status, deleted),
    INDEX idx_emp_salary_struct (tenant_id, structure_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工薪资档案';

-- 3) 工资条
CREATE TABLE pay_slip (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    employee_id         BIGINT          NOT NULL,
    gross_salary        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    total_deduction     DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    net_salary          DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/calculated/confirmed/paid',
    calculated_at       DATETIME        DEFAULT NULL,
    confirmed_at        DATETIME        DEFAULT NULL,
    paid_at             DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #7 类: UNIQUE(tenant_id, employee_id, period, deleted)
    UNIQUE KEY uk_slip_emp_period (tenant_id, employee_id, period, deleted),
    INDEX idx_slip_period (tenant_id, period, status, deleted),
    INDEX idx_slip_emp (tenant_id, employee_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资条';

-- 4) 工资条明细
CREATE TABLE pay_slip_item (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    slip_id             BIGINT          NOT NULL,
    item_type           VARCHAR(16)     NOT NULL                COMMENT 'earning/deduction',
    item_code           VARCHAR(64)     NOT NULL,
    item_name           VARCHAR(128)    DEFAULT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    formula             VARCHAR(255)    DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_slip_item_slip (slip_id, deleted),
    INDEX idx_slip_item_type (tenant_id, item_type, deleted),
    CONSTRAINT chk_slip_item_type CHECK (item_type IN ('earning','deduction'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资条明细';

-- 5) 个税记录 (special_deduction 加密)
CREATE TABLE pay_tax (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    slip_id             BIGINT          NOT NULL,
    employee_id         BIGINT          NOT NULL,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    taxable_income      DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    tax_rate            DECIMAL(6, 4)   NOT NULL DEFAULT 0      COMMENT '0~0.45',
    cumulative_income   DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    cumulative_tax      DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    special_deduction   VARBINARY(2048) DEFAULT NULL            COMMENT 'AES-GCM 加密 JSON',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_slip (slip_id, deleted),
    INDEX idx_tax_emp_period (tenant_id, employee_id, period, deleted),
    INDEX idx_tax_period (tenant_id, period, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个税记录';

-- 6) 社保
CREATE TABLE pay_social_security (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT          NOT NULL,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    base_amount         DECIMAL(18, 2)  NOT NULL                COMMENT '社保基数',
    employee_amount     DECIMAL(18, 2)  NOT NULL DEFAULT 0      COMMENT '个人部分',
    employer_amount     DECIMAL(18, 2)  NOT NULL DEFAULT 0      COMMENT '公司部分',
    items               JSON            DEFAULT NULL            COMMENT '各项明细 {pension, medical, ...}',
    status              VARCHAR(16)     NOT NULL DEFAULT 'calculated' COMMENT 'calculated/declared/paid',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ss_emp_period (tenant_id, employee_id, period, deleted),
    INDEX idx_ss_period (tenant_id, period, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社保';

-- 7) 银行报盘文件
CREATE TABLE pay_bank_file (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    period              VARCHAR(7)      NOT NULL                COMMENT 'yyyy-MM',
    bank_code           VARCHAR(16)     NOT NULL                COMMENT 'ccb/icbc/cmb/abc/boc/spdb/comm/cib/pingan',
    file_path           VARCHAR(255)    NOT NULL,
    file_md5            VARCHAR(64)     NOT NULL,
    employee_count      INT             NOT NULL DEFAULT 0,
    total_amount        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'generated' COMMENT 'generated/sent/confirmed/failed',
    generated_at        DATETIME        DEFAULT CURRENT_TIMESTAMP,
    sent_at             DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求 #13: UNIQUE(tenant_id, period, bank_code, deleted) 保证内容冻结
    UNIQUE KEY uk_bank_period_code (tenant_id, period, bank_code, deleted),
    INDEX idx_bank_period (tenant_id, period, status, deleted),
    INDEX idx_bank_code (tenant_id, bank_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='银行报盘文件';

-- 8) 工资条已读
CREATE TABLE pay_payslip_read (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    slip_id             BIGINT          NOT NULL,
    employee_id         BIGINT          NOT NULL,
    read_at             DATETIME        DEFAULT CURRENT_TIMESTAMP,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_read_slip_emp (slip_id, employee_id, deleted),
    INDEX idx_read_slip (slip_id, deleted),
    INDEX idx_read_emp (tenant_id, employee_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资条已读';

-- ============================================================
-- TODO P5:
-- 1) pay_employee_salary.idx_effective_date (tenant_id, effective_from, effective_to)
-- 2) pay_slip.idx_slip_status_emp (tenant_id, status, employee_id)
-- 3) pay_tax 列密文索引 (special_deduction HMAC 列) 支持按专项附加项目检索
-- 4) bank_file 真实格式: 各银行 (CCB/ICBC/CMB) 定制 (当前为占位文本)
-- 5) employee_id 来源跨服务联调 (lumen-hr Feign client)
-- ============================================================