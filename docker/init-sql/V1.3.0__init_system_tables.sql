DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type (
    dict_id            BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    dict_name          VARCHAR(100)    DEFAULT '',
    dict_type          VARCHAR(100)    NOT NULL DEFAULT '',
    status             CHAR(1)         NOT NULL DEFAULT '0',
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_dict_tenant_type (tenant_id, dict_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data (
    dict_code          BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    dict_sort          INT             NOT NULL DEFAULT 0,
    dict_label         VARCHAR(100)    DEFAULT '',
    dict_value         VARCHAR(100)    DEFAULT '',
    dict_type          VARCHAR(100)    NOT NULL DEFAULT '',
    css_class          VARCHAR(50)     DEFAULT NULL,
    list_class         VARCHAR(50)     DEFAULT NULL,
    is_default         CHAR(1)         NOT NULL DEFAULT 'N',
    status             CHAR(1)         NOT NULL DEFAULT '0',
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (dict_code),
    UNIQUE KEY uk_dict_data_tenant_type_value (tenant_id, dict_type, dict_value, deleted),
    KEY idx_dict_data_type (dict_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据';

DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    config_id          BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    config_name        VARCHAR(100)    DEFAULT NULL,
    config_key         VARCHAR(100)    NOT NULL DEFAULT '',
    config_value       VARCHAR(500)    DEFAULT NULL,
    config_type        CHAR(1)         NOT NULL DEFAULT 'N',
    is_builtin         TINYINT         NOT NULL DEFAULT 0,
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_config_tenant_key (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数配置';

DROP TABLE IF EXISTS sys_job;
CREATE TABLE sys_job (
    job_id             BIGINT          NOT NULL AUTO_INCREMENT,
    job_name           VARCHAR(100)    NOT NULL DEFAULT '',
    job_group          VARCHAR(100)    NOT NULL DEFAULT 'DEFAULT',
    invoke_target      VARCHAR(500)    NOT NULL,
    cron_expression    VARCHAR(200)    DEFAULT NULL,
    misfire_policy     VARCHAR(20)     DEFAULT '3',
    concurrent         CHAR(1)         DEFAULT '1',
    status             CHAR(1)         NOT NULL DEFAULT '0',
    remark             VARCHAR(500)    DEFAULT NULL,
    create_by          BIGINT          NOT NULL DEFAULT 0,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT          NOT NULL DEFAULT 0,
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';

DROP TABLE IF EXISTS sys_job_log;
CREATE TABLE sys_job_log (
    job_log_id         BIGINT          NOT NULL AUTO_INCREMENT,
    job_id             BIGINT          NOT NULL,
    job_name           VARCHAR(100)    NOT NULL,
    job_group          VARCHAR(100)    NOT NULL,
    invoke_target      VARCHAR(500)    NOT NULL,
    job_message        VARCHAR(500)    DEFAULT NULL,
    status             CHAR(1)         NOT NULL DEFAULT '0',
    exception_info     TEXT            DEFAULT NULL,
    start_time         DATETIME        DEFAULT NULL,
    stop_time          DATETIME        DEFAULT NULL,
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_log_id),
    KEY idx_job_log_job (job_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志';

DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
    oper_id            BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    title              VARCHAR(50)     DEFAULT '',
    business_type      INT             NOT NULL DEFAULT 0,
    method             VARCHAR(200)    DEFAULT '',
    request_method     VARCHAR(10)     DEFAULT '',
    operator_type      INT             NOT NULL DEFAULT 0,
    oper_name          VARCHAR(50)    DEFAULT '',
    dept_name          VARCHAR(50)    DEFAULT '',
    oper_url           VARCHAR(255)    DEFAULT '',
    oper_ip            VARCHAR(64)    DEFAULT '',
    oper_param         VARCHAR(2000)   DEFAULT '',
    json_result        VARCHAR(2000)   DEFAULT '',
    -- status: INT (HTTP-like 0=success 4xx/5xx=fail); differs from sys_user.status CHAR(1) by design
    status             INT             NOT NULL DEFAULT 0,
    error_msg          VARCHAR(2000)   DEFAULT '',
    cost_ms            BIGINT          NOT NULL DEFAULT 0,
    oper_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (oper_id),
    KEY idx_oper_log_time (oper_time),
    KEY idx_oper_log_business (business_type, oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

DROP TABLE IF EXISTS sys_logininfor;
CREATE TABLE sys_logininfor (
    info_id            BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    user_name          VARCHAR(50)    DEFAULT '',
    ipaddr             VARCHAR(64)    DEFAULT '',
    login_location     VARCHAR(100)   DEFAULT '',
    browser            VARCHAR(50)    DEFAULT '',
    os                 VARCHAR(50)    DEFAULT '',
    status             CHAR(1)        NOT NULL DEFAULT '0',
    msg                VARCHAR(255)   DEFAULT '',
    login_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (info_id),
    KEY idx_logininfor_time (login_time),
    KEY idx_logininfor_user (user_name, login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';
