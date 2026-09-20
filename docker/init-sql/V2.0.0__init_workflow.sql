-- ============================================================
-- V2.0.0 — P3: workflow engine tables (wf_*)
-- Owned by lumen-platform (single source of truth for Flyway)
-- ============================================================

-- 流程定义
CREATE TABLE wf_definition (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    def_key         VARCHAR(64)     NOT NULL COMMENT '流程定义KEY',
    name            VARCHAR(128)    NOT NULL,
    version         INT             NOT NULL DEFAULT 1,
    category        VARCHAR(32)     DEFAULT NULL,
    bpmn_xml        LONGTEXT        COMMENT 'BPMN 2.0 XML',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已下线',
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_def_key_version (def_key, version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义';

-- 流程实例
CREATE TABLE wf_instance (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    definition_id   BIGINT          NOT NULL,
    def_key         VARCHAR(64)     NOT NULL,
    business_key    VARCHAR(128)    NOT NULL COMMENT '业务单据ID',
    tenant_id       BIGINT          NOT NULL DEFAULT 0,
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0=进行中 1=已完成 2=已取消',
    current_node_key VARCHAR(64)    DEFAULT NULL,
    variables       JSON            DEFAULT NULL,
    starter         BIGINT          NOT NULL,
    start_time      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    end_time        DATETIME        DEFAULT NULL,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_business_key (business_key),
    INDEX idx_starter (starter, status),
    INDEX idx_def_key (def_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例';

-- 待办任务
CREATE TABLE wf_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    instance_id     BIGINT          NOT NULL,
    node_key        VARCHAR(64)     NOT NULL,
    node_name       VARCHAR(128)    DEFAULT NULL,
    assignee        BIGINT          DEFAULT NULL,
    candidate_users JSON            DEFAULT NULL COMMENT '候选人数组',
    candidate_roles JSON            DEFAULT NULL,
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0=待办 1=已办 2=已转办 3=已加签 4=已驳回',
    due_time        DATETIME        DEFAULT NULL,
    complete_time   DATETIME        DEFAULT NULL,
    comment         TEXT            DEFAULT NULL,
    create_by       BIGINT          DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_assignee (assignee, status),
    INDEX idx_instance (instance_id, status),
    INDEX idx_due (due_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办任务';

-- 任务操作历史归档 (任务完成后写入)
CREATE TABLE wf_task_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    instance_id     BIGINT          NOT NULL,
    node_key        VARCHAR(64)     NOT NULL,
    assignee        BIGINT          DEFAULT NULL,
    action          VARCHAR(32)     NOT NULL COMMENT 'done/transfer/addSign/reject',
    comment         TEXT            DEFAULT NULL,
    operated_by     BIGINT          NOT NULL,
    operated_time   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_instance (instance_id),
    INDEX idx_action_time (action, operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务操作历史';