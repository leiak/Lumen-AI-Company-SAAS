-- ============================================================
-- V3.8.0 — P4-B3.2: BI 报表模块表 (bi_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- Module: lumen-bi (8 services, 6 tables)
-- ============================================================

-- ------------------------------------------------------------
-- 1) 指标定义 (口径、维度、度量)
-- ------------------------------------------------------------
CREATE TABLE bi_metric (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL                COMMENT '指标 code, 业务唯一',
    name                VARCHAR(128)    NOT NULL,
    category            VARCHAR(32)     NOT NULL                COMMENT 'sales/finance/hr/procurement/inventory/custom',
    definition          JSON            DEFAULT NULL            COMMENT '{sql, params, dimensions, measures, filters}',
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    version             INT             NOT NULL DEFAULT 1,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求: UNIQUE(tenant_id, code, deleted)
    UNIQUE KEY uk_metric_code_tenant (tenant_id, code, deleted),
    INDEX idx_metric_category (tenant_id, category, status, deleted),
    INDEX idx_metric_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';

-- ------------------------------------------------------------
-- 2) 数据集 (SQL/API/Join 配置)
-- ------------------------------------------------------------
CREATE TABLE bi_dataset (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL                COMMENT '数据集 code, 业务唯一',
    name                VARCHAR(128)    NOT NULL,
    source_type         VARCHAR(16)     NOT NULL DEFAULT 'sql'   COMMENT 'sql/api/join',
    model               JSON            DEFAULT NULL            COMMENT '数据集模型 {sql, fields, joins, ...}',
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_code_tenant (tenant_id, code, deleted),
    INDEX idx_dataset_source (tenant_id, source_type, status, deleted),
    INDEX idx_dataset_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集';

-- ------------------------------------------------------------
-- 3) 看板 (状态机: draft/published/archived)
-- ------------------------------------------------------------
CREATE TABLE bi_dashboard (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL                COMMENT '看板 code, 业务唯一',
    name                VARCHAR(128)    NOT NULL,
    layout              JSON            DEFAULT NULL            COMMENT '看板布局配置 (尺寸/主题/网格)',
    status              VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
    published_at        DATETIME        DEFAULT NULL            COMMENT '发布时间戳 (published 后不可改 layout)',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dashboard_code_tenant (tenant_id, code, deleted),
    INDEX idx_dashboard_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板';

-- ------------------------------------------------------------
-- 4) 看板组件 (widget)
-- ------------------------------------------------------------
CREATE TABLE bi_widget (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    dashboard_id        BIGINT          NOT NULL                COMMENT 'cross-ref bi_dashboard.id',
    name                VARCHAR(128)    NOT NULL,
    type                VARCHAR(16)     NOT NULL                COMMENT 'table/chart/number/gauge/pivot/filter',
    dataset_id          BIGINT          DEFAULT NULL            COMMENT 'cross-ref bi_dataset.id',
    metric_id           BIGINT          DEFAULT NULL            COMMENT 'cross-ref bi_metric.id',
    config              JSON            DEFAULT NULL            COMMENT '组件配置 (颜色/格式/选项)',
    position            JSON            DEFAULT NULL            COMMENT '{x, y, w, h}',
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_widget_dashboard (tenant_id, dashboard_id, status, deleted),
    INDEX idx_widget_dataset (tenant_id, dataset_id, deleted),
    INDEX idx_widget_metric (tenant_id, metric_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板组件';

-- ------------------------------------------------------------
-- 5) 报表 (预定义报表 + 调度)
-- ------------------------------------------------------------
CREATE TABLE bi_report (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)     NOT NULL                COMMENT '报表 code, 业务唯一',
    name                VARCHAR(128)    NOT NULL,
    dataset_id          BIGINT          DEFAULT NULL            COMMENT 'cross-ref bi_dataset.id',
    template            JSON            DEFAULT NULL            COMMENT '报表模板配置',
    schedule            VARCHAR(16)     NOT NULL DEFAULT 'manual' COMMENT 'manual/daily/weekly/monthly',
    cron_expression     VARCHAR(128)    DEFAULT NULL            COMMENT 'Spring 6-field cron (秒 分 时 日 月 周)',
    format              VARCHAR(16)     NOT NULL DEFAULT 'pdf'  COMMENT 'pdf/excel/csv',
    recipients          JSON            DEFAULT NULL            COMMENT '收件人邮箱列表',
    status              VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    last_run_at         DATETIME        DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_code_tenant (tenant_id, code, deleted),
    INDEX idx_report_schedule (tenant_id, schedule, status, deleted),
    INDEX idx_report_status (tenant_id, status, deleted),
    INDEX idx_report_dataset (tenant_id, dataset_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表';

-- ------------------------------------------------------------
-- 6) 资源权限 (resourceType: dashboard/metric/report/dataset)
-- ------------------------------------------------------------
CREATE TABLE bi_permission (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    resource_type       VARCHAR(16)     NOT NULL                COMMENT 'dashboard/metric/report/dataset',
    resource_id         BIGINT          NOT NULL,
    principal_type      VARCHAR(16)     NOT NULL                COMMENT 'user/role/dept',
    principal_id        BIGINT          NOT NULL,
    permission          VARCHAR(16)     NOT NULL                COMMENT 'view/edit/admin',
    granted_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    granted_by          BIGINT          DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    create_by           BIGINT          DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT          DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    -- 安全要求: UNIQUE(resource_type, resource_id, principal_type, principal_id, deleted)
    UNIQUE KEY uk_permission_unique (
        resource_type, resource_id, principal_type, principal_id, deleted),
    INDEX idx_permission_resource (tenant_id, resource_type, resource_id, deleted),
    INDEX idx_permission_principal (tenant_id, principal_type, principal_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI 资源权限';

-- ============================================================
-- TODO P5:
-- 1) BI 服务切到 bi_reader 只读账号 (jdbc URL 已留配置位)
-- 2) SyncService 真实 ETL 同步 (Doris/ClickHouse)
-- 3) Quartz 真实调度替换 ReportService.schedule() 内存计算
-- 4) message-center 集成 sendReport (当前 TODO)
-- 5) WidgetService metric path 接入 JdbcTemplate 执行 (当前只返回 source 描述)
-- 6) PermissionService principal_type=dept 走 lumen-org 树查询
-- ============================================================