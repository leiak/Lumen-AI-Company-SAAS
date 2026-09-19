-- ============================================================
-- V3.0.0 — P4-B1.1: HR module tables (hr_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 员工档案
CREATE TABLE hr_employee (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          DEFAULT NULL             COMMENT '关联 sys_user.user_id',
    code            VARCHAR(64)     NOT NULL                 COMMENT '业务编号，租户内唯一',
    name            VARCHAR(64)     NOT NULL                 COMMENT '姓名',
    id_card_enc     VARCHAR(500)    DEFAULT NULL             COMMENT '身份证号（AES-256-GCM）',
    mobile_enc      VARCHAR(500)    DEFAULT NULL             COMMENT '手机号（AES-256-GCM）',
    hire_date       DATE            DEFAULT NULL             COMMENT '入职日期',
    dept_id         BIGINT          DEFAULT NULL,
    post_id         BIGINT          DEFAULT NULL,
    level_id        BIGINT          DEFAULT NULL,
    status          TINYINT         NOT NULL DEFAULT 1       COMMENT '0=在职 1=试用期 2=离职 3=停薪留职',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_tenant_code (tenant_id, code, deleted),
    KEY idx_employee_user (user_id),
    KEY idx_employee_dept (dept_id, status),
    KEY idx_employee_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案';

-- 招聘需求 JD
CREATE TABLE hr_recruit_job (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255)    NOT NULL,
    dept_id         BIGINT          DEFAULT NULL,
    post_id         BIGINT          DEFAULT NULL,
    headcount       INT             NOT NULL DEFAULT 1,
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=草稿 1=已发布 2=已关闭 3=已招满',
    published_at    DATETIME        DEFAULT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_job_tenant_status (tenant_id, status, deleted),
    KEY idx_job_dept (dept_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘需求JD';

-- 候选人
CREATE TABLE hr_recruit_candidate (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    job_id          BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    mobile_enc      VARCHAR(500)    DEFAULT NULL             COMMENT '手机号（AES-256-GCM）',
    email_enc       VARCHAR(500)    DEFAULT NULL             COMMENT '邮箱（AES-256-GCM）',
    resume_url      VARCHAR(500)    DEFAULT NULL,
    stage           TINYINT         NOT NULL DEFAULT 0       COMMENT '0=筛选 1=初试 2=复试 3=终试 4=已发Offer 5=已入职 6=已淘汰',
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=进行中 1=已Offer 2=已入职 3=已淘汰',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_candidate_tenant_job (tenant_id, job_id, deleted),
    KEY idx_candidate_stage (stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人';

-- Offer 记录
CREATE TABLE hr_offer (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    candidate_id    BIGINT          NOT NULL,
    salary          DECIMAL(15,2)   DEFAULT NULL,
    start_date      DATE            DEFAULT NULL,
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=草稿 1=已发送 2=已接受 3=已拒绝 4=已撤回',
    sent_at         DATETIME        DEFAULT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_offer_tenant_candidate (tenant_id, candidate_id, deleted),
    KEY idx_offer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offer记录';

-- 入职清单
CREATE TABLE hr_onboarding (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT          NOT NULL,
    checklist       JSON            DEFAULT NULL             COMMENT '清单 Map<String,Boolean>',
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=未开始 1=进行中 2=已完成',
    completed_at    DATETIME        DEFAULT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_onboarding_tenant_employee (tenant_id, employee_id, deleted),
    KEY idx_onboarding_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职清单';

-- 调岗记录（审计轨迹）
CREATE TABLE hr_transfer (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT          NOT NULL,
    from_dept_id    BIGINT          DEFAULT NULL,
    to_dept_id      BIGINT          DEFAULT NULL,
    from_post_id    BIGINT          DEFAULT NULL,
    to_post_id      BIGINT          DEFAULT NULL,
    effective_at    DATE            DEFAULT NULL             COMMENT '生效日期',
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=待审批 1=已通过 2=已驳回 3=已生效',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_transfer_tenant_employee (tenant_id, employee_id, deleted),
    KEY idx_transfer_status (status, effective_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调岗记录';

-- 离职申请
CREATE TABLE hr_resignation (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT          NOT NULL,
    reason          VARCHAR(1000)   DEFAULT NULL,
    submit_at       DATETIME        DEFAULT CURRENT_TIMESTAMP,
    effective_at    DATE            DEFAULT NULL             COMMENT '离职生效日期',
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=已提交 1=审批中 2=已通过 3=已生效 4=已驳回 5=已撤销',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_resignation_tenant_employee (tenant_id, employee_id, deleted),
    KEY idx_resignation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职申请';

-- 绩效周期
CREATE TABLE hr_performance_cycle (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=未开始 1=进行中 2=已结束',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pcycle_tenant_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效周期';

-- 绩效评分
CREATE TABLE hr_performance_score (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    cycle_id        BIGINT          NOT NULL,
    employee_id     BIGINT          NOT NULL,
    score           DECIMAL(5,2)    NOT NULL                 COMMENT '0-100',
    comment         VARCHAR(2000)   DEFAULT NULL,
    submitted_at    DATETIME        DEFAULT CURRENT_TIMESTAMP,
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pscore_tenant_cycle (tenant_id, cycle_id, deleted),
    KEY idx_pscore_employee (employee_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效评分';

-- 培训计划
CREATE TABLE hr_training_plan (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)    NOT NULL,
    start_at        DATETIME        NOT NULL,
    end_at          DATETIME        NOT NULL,
    capacity        INT             NOT NULL DEFAULT 50,
    status          TINYINT         NOT NULL DEFAULT 0       COMMENT '0=草稿 1=已发布 2=进行中 3=已结束 4=已取消',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tplan_tenant_status (tenant_id, status, deleted),
    KEY idx_tplan_window (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训计划';

-- 培训记录
CREATE TABLE hr_training_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plan_id         BIGINT          NOT NULL,
    employee_id     BIGINT          NOT NULL,
    completed_at    DATETIME        DEFAULT NULL,
    score           DECIMAL(5,2)    DEFAULT NULL             COMMENT '0-100',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_tenant_plan_employee (tenant_id, plan_id, employee_id, deleted),
    KEY idx_record_employee (employee_id, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训记录';
