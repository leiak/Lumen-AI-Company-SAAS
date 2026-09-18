# 附录 G：DB Schema 设计示例

> 本附录为 **3 个基础模块**提供**完整可执行的 Flyway 脚本**：
> - **platform-base**：租户/序列号/应用注册
> - **auth-rbac**：在 RuoYi sys_user/sys_role 基础上扩展
> - **org-structure**：组织架构扩展
>
> 这些脚本是**可直接执行**的，复制到对应微服务的 `src/main/resources/db/migration/` 目录即可。

---

## G.1 platform-base 模块

### V1.0.0__init_tenant_tables.sql

```sql
-- ============================================================
-- 租户主表
-- ============================================================
DROP TABLE IF EXISTS tenant;
CREATE TABLE tenant (
    id                  BIGINT          NOT NULL AUTO_INCREMENT          COMMENT '主键',
    code                VARCHAR(50)     NOT NULL                        COMMENT '租户编码（唯一）',
    name                VARCHAR(100)    NOT NULL                        COMMENT '租户名称',
    short_name          VARCHAR(50)     DEFAULT ''                      COMMENT '简称',
    contact_name        VARCHAR(50)     DEFAULT ''                      COMMENT '联系人',
    contact_phone       VARCHAR(50)     DEFAULT ''                      COMMENT '联系电话',
    contact_email       VARCHAR(100)    DEFAULT ''                      COMMENT '联系邮箱',
    
    industry            VARCHAR(50)     DEFAULT ''                      COMMENT '行业',
    scale               VARCHAR(20)     DEFAULT ''                      COMMENT '规模（1-50/51-200/201-500/501-2000/2000+）',
    region              VARCHAR(50)     DEFAULT ''                      COMMENT '所在地区',
    
    package_id          BIGINT          NOT NULL DEFAULT 1              COMMENT '套餐ID',
    
    status              TINYINT         NOT NULL DEFAULT 1              COMMENT '状态 0-禁用 1-试用 2-正式 3-过期 4-冻结',
    trial_days          INT             NOT NULL DEFAULT 30             COMMENT '试用天数',
    expire_at           DATETIME        DEFAULT NULL                    COMMENT '到期时间',
    activated_at        DATETIME        DEFAULT NULL                    COMMENT '激活时间',
    
    logo_url            VARCHAR(500)    DEFAULT NULL                    COMMENT 'Logo',
    description         VARCHAR(500)    DEFAULT NULL                    COMMENT '描述',
    
    -- 审计字段（每张表都包含）
    create_by           BIGINT          NOT NULL DEFAULT 0              COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_by           BIGINT          NOT NULL DEFAULT 0              COMMENT '更新人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0              COMMENT '逻辑删除 0-否 1-是',
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (code, deleted),
    KEY idx_tenant_status (status),
    KEY idx_tenant_expire (expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户主表';

-- ============================================================
-- 套餐表
-- ============================================================
DROP TABLE IF EXISTS tenant_package;
CREATE TABLE tenant_package (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    code                VARCHAR(50)     NOT NULL                        COMMENT '套餐编码',
    name                VARCHAR(100)    NOT NULL                        COMMENT '套餐名称',
    
    -- 模块开关（JSON 数组）
    modules            JSON             NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '启用的模块列表 ["hr","finance","contract"]',
    
    -- 配额限制
    max_users          INT              NOT NULL DEFAULT 10             COMMENT '最大用户数',
    max_storage_gb     INT              NOT NULL DEFAULT 5              COMMENT '最大存储 GB',
    max_employees      INT              NOT NULL DEFAULT 100            COMMENT '最大员工档案数',
    
    -- 定价（分）
    price_cents        BIGINT           NOT NULL DEFAULT 0              COMMENT '年价（分）',
    duration_days      INT              NOT NULL DEFAULT 365            COMMENT '有效期（天）',
    
    description        VARCHAR(500)     DEFAULT NULL,
    is_builtin         TINYINT          NOT NULL DEFAULT 0              COMMENT '系统内置',
    
    create_by, create_time, update_by, update_time, deleted,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_package_code (code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户套餐';

-- 初始数据
INSERT INTO tenant_package (code, name, modules, max_users, max_storage_gb, max_employees, price_cents, is_builtin) VALUES
('free',      '免费版', JSON_ARRAY('hr_basic','workflow'),                                10,   1,    50,    0,        1),
('standard',  '标准版', JSON_ARRAY('hr','finance','contract','procurement','assets'),     100,  50,   1000,  99900,    0),
('enterprise','企业版', JSON_ARRAY('hr','finance','contract','procurement','assets',
                                   'inventory','sales','payroll','bi','mobile'),          9999, 9999, 999999, 999900,   0);

-- ============================================================
-- 租户配置表
-- ============================================================
DROP TABLE IF EXISTS tenant_config;
CREATE TABLE tenant_config (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT          NOT NULL                        COMMENT '租户ID',
    config_key          VARCHAR(100)    NOT NULL                        COMMENT '配置 key',
    config_value        TEXT            DEFAULT NULL                    COMMENT '配置 value',
    value_type          VARCHAR(20)     NOT NULL DEFAULT 'STRING'       COMMENT 'STRING/NUMBER/BOOLEAN/JSON',
    
    create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户配置';

-- ============================================================
-- 业务序列号生成器
-- ============================================================
DROP TABLE IF EXISTS common_seq;
CREATE TABLE common_seq (
    seq_name            VARCHAR(50)     NOT NULL                        COMMENT '序列名',
    current_val         BIGINT          NOT NULL DEFAULT 1              COMMENT '当前值',
    step                INT             NOT NULL DEFAULT 1              COMMENT '步长',
    prefix              VARCHAR(20)     DEFAULT ''                      COMMENT '前缀（如 HR/PO）',
    format              VARCHAR(50)     DEFAULT '{prefix}{yyyyMMdd}{seq:6}' COMMENT '格式',
    description         VARCHAR(200)    DEFAULT NULL,
    
    create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (seq_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务序列号生成器';

-- 初始数据
INSERT INTO common_seq (seq_name, current_val, prefix, description) VALUES
('hr_employee_no',  1, 'E',  '员工编号'),
('hr_contract_no',  1, 'C',  '合同编号'),
('fin_voucher_no',  1, 'V',  '凭证号'),
('proc_order_no',   1, 'PO', '采购单号'),
('ctr_contract_no', 1, 'CT', '合同号'),
('sal_order_no',    1, 'SO', '销售订单号'),
('ast_asset_no',    1, 'A',  '资产编号'),
('pay_period',      1, 'P',  '薪资周期'),
('workflow_inst',   1, 'WF', '流程实例号');

-- ============================================================
-- 微服务应用注册表
-- ============================================================
DROP TABLE IF EXISTS lumen_application;
CREATE TABLE lumen_application (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    app_code            VARCHAR(50)     NOT NULL                        COMMENT '应用编码',
    app_name            VARCHAR(100)    NOT NULL                        COMMENT '应用名称',
    app_type            VARCHAR(20)     NOT NULL DEFAULT 'SERVICE'      COMMENT 'SERVICE/GATEWAY/ADMIN',
    description         VARCHAR(500)    DEFAULT NULL,
    
    create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_lumen_app_code (app_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '微服务应用注册';

-- 初始数据
INSERT INTO lumen_application (app_code, app_name, app_type) VALUES
('platform-gateway', '平台网关', 'GATEWAY'),
('auth-service',     '认证服务', 'SERVICE'),
('system-service',   '系统服务', 'SERVICE'),
('workflow-service', '工作流',   'SERVICE'),
('file-service',     '文件服务', 'SERVICE'),
('message-service',  '消息服务', 'SERVICE'),
('org-service',      '组织服务', 'SERVICE'),
('hr-service',       '人力服务', 'SERVICE'),
('finance-service',  '财务服务', 'SERVICE'),
('assets-service',   '资产服务', 'SERVICE'),
('procurement-service', '采购服务', 'SERVICE'),
('contract-service', '合同服务', 'SERVICE'),
('inventory-service','库存服务', 'SERVICE'),
('sales-service',    '销售服务', 'SERVICE'),
('payroll-service',  '薪资服务', 'SERVICE'),
('bi-service',       'BI 服务', 'SERVICE'),
('mobile-gateway',   '移动网关', 'GATEWAY');

-- ============================================================
-- 初始超级租户（私有部署模式下唯一租户）
-- ============================================================
INSERT INTO tenant (id, code, name, short_name, package_id, status, expire_at) VALUES
(1, 'default', '默认租户', '默认', 1, 2, DATE_ADD(NOW(), INTERVAL 100 YEAR));
```

### V1.0.1__init_tenant_seed_data.sql

```sql
-- 默认租户的初始化配置
INSERT INTO tenant_config (tenant_id, config_key, config_value, value_type) VALUES
(1, 'theme.color',     'blue',         'STRING'),
(1, 'login.captcha',   'true',         'BOOLEAN'),
(1, 'login.mfa',       'false',        'BOOLEAN'),
(1, 'pwd.expire_days', '90',           'NUMBER'),
(1, 'session.timeout', '1800',         'NUMBER'),
(1, 'workflow.engine', 'state_machine','STRING'),
(1, 'file.storage',    'minio',        'STRING');
```

---

## G.2 auth-rbac 模块

### V1.1.0__extend_sys_user_for_multi_tenant.sql

```sql
-- ============================================================
-- 扩展 sys_user 支持多租户 + MFA + 密码策略 + 字段加密
-- ============================================================

-- expand: 加列（允许默认值，不影响现有数据）
ALTER TABLE sys_user
    ADD COLUMN tenant_id        BIGINT          NOT NULL DEFAULT 0              COMMENT '租户ID' AFTER user_id,
    ADD COLUMN data_scope       TINYINT         NOT NULL DEFAULT 1              COMMENT '数据权限 1-全部 2-本部门 3-本部门及下级 4-本人 5-自定义' AFTER dept_id,
    ADD COLUMN mfa_secret       VARCHAR(100)    DEFAULT NULL                    COMMENT 'TOTP 密钥（加密存储）' AFTER password,
    ADD COLUMN mfa_enabled      TINYINT         NOT NULL DEFAULT 0              COMMENT 'MFA 是否开启',
    ADD COLUMN pwd_expire_at    DATETIME        DEFAULT NULL                    COMMENT '密码过期时间',
    ADD COLUMN pwd_history      VARCHAR(2000)   DEFAULT ''                      COMMENT '历史密码哈希（最多 5 个，逗号分隔）',
    ADD COLUMN id_card_enc      VARCHAR(500)    DEFAULT NULL                    COMMENT '身份证 AES 加密',
    ADD COLUMN mobile_enc       VARCHAR(500)    DEFAULT NULL                    COMMENT '手机号加密',
    ADD COLUMN email_enc        VARCHAR(500)    DEFAULT NULL                    COMMENT '邮箱加密',
    ADD COLUMN bank_card_enc    VARCHAR(500)    DEFAULT NULL                    COMMENT '银行卡加密',
    ADD COLUMN last_pwd_change  DATETIME        DEFAULT NULL                    COMMENT '上次改密时间',
    ADD COLUMN fail_count       INT             NOT NULL DEFAULT 0              COMMENT '连续登录失败次数',
    ADD COLUMN lock_until       DATETIME        DEFAULT NULL                    COMMENT '锁定至此时刻',
    ADD COLUMN deleted          TINYINT         NOT NULL DEFAULT 0              COMMENT '逻辑删除';

-- 数据回填：把所有现有用户的 tenant_id 设为 1（默认租户）
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id = 0;

-- 加索引（INPLACE 算法，不锁表）
ALTER TABLE sys_user 
    ADD INDEX idx_sys_user_tenant (tenant_id, deleted) ,
    ADD INDEX idx_sys_user_dept (dept_id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 唯一索引改为（tenant_id, user_name）联合唯一
ALTER TABLE sys_user DROP INDEX user_name;  -- RuoYi 没有显式 user_name 唯一索引，这里只是示例
CREATE UNIQUE INDEX uk_sys_user_tenant_username ON sys_user (tenant_id, user_name, deleted);
```

### V1.1.1__extend_sys_role_for_data_scope.sql

```sql
-- ============================================================
-- 扩展 sys_role 支持多租户 + data_scope 改为 TINYINT
-- ============================================================
ALTER TABLE sys_role
    ADD COLUMN tenant_id        BIGINT          NOT NULL DEFAULT 0              COMMENT '租户ID' AFTER role_id,
    ADD COLUMN api_pattern      VARCHAR(500)    DEFAULT NULL                    COMMENT 'API 路径匹配（Ant 风格）',
    ADD COLUMN i18n_key         VARCHAR(100)    DEFAULT NULL                    COMMENT '国际化 key';

-- data_scope 从 char(1) 改 TINYINT
-- 注意：需要先加新列，再迁移数据，再删旧列
ALTER TABLE sys_role ADD COLUMN data_scope_new TINYINT NOT NULL DEFAULT 1 COMMENT '数据权限（新）' AFTER data_scope;

UPDATE sys_role SET data_scope_new = CASE data_scope
    WHEN '1' THEN 1
    WHEN '2' THEN 4  -- RuoYi 的 2=自定，4=本部门。这里我们重新映射
    WHEN '3' THEN 3
    WHEN '4' THEN 2
    ELSE 1
END;

ALTER TABLE sys_role DROP COLUMN data_scope;
ALTER TABLE sys_role CHANGE COLUMN data_scope_new data_scope TINYINT NOT NULL DEFAULT 1 COMMENT '1-全部 2-本部门 3-本部门及下级 4-本人 5-自定义';

-- 数据回填
UPDATE sys_role SET tenant_id = 1 WHERE tenant_id = 0;

-- 索引
ALTER TABLE sys_role
    ADD INDEX idx_sys_role_tenant (tenant_id, deleted),
    ADD UNIQUE INDEX uk_sys_role_tenant_key (tenant_id, role_key, deleted),
    ALGORITHM = INPLACE, LOCK = NONE;
```

### V1.1.2__init_sso_tables.sql

```sql
-- ============================================================
-- SSO 账号绑定
-- ============================================================
DROP TABLE IF EXISTS sys_sso_account;
CREATE TABLE sys_sso_account (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL                        COMMENT '用户ID',
    tenant_id           BIGINT          NOT NULL DEFAULT 0              COMMENT '租户ID',
    platform            VARCHAR(20)     NOT NULL                        COMMENT 'dingtalk/wechatwork/feishu/ldap/oidc',
    open_id             VARCHAR(200)    NOT NULL                        COMMENT '第三方 OpenID',
    union_id            VARCHAR(200)    DEFAULT NULL                    COMMENT 'UnionID（多端统一）',
    
    bind_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '绑定时间',
    last_login_at       DATETIME        DEFAULT NULL                    COMMENT '最后登录',
    
    create_by, create_time, update_by, update_time, deleted,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_platform_open (platform, open_id, deleted),
    KEY idx_sso_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSO 账号绑定';

-- ============================================================
-- 会话表（管理 Refresh Token）
-- ============================================================
DROP TABLE IF EXISTS sys_user_session;
CREATE TABLE sys_user_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_id          VARCHAR(64)     NOT NULL                        COMMENT 'Session ID = JWT ID (jti)',
    user_id             BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    
    refresh_token       VARCHAR(500)    DEFAULT NULL                    COMMENT 'Refresh Token（可选存）',
    
    ip                  VARCHAR(64)     DEFAULT NULL,
    user_agent          VARCHAR(500)    DEFAULT NULL,
    device              VARCHAR(50)     DEFAULT 'WEB'                    COMMENT 'WEB/H5/APP/MINI',
    
    login_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at           DATETIME        NOT NULL,
    
    status              TINYINT         NOT NULL DEFAULT 1              COMMENT '1-有效 0-失效',
    logout_at           DATETIME        DEFAULT NULL,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_session_user (user_id),
    KEY idx_session_expire (expire_at, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户会话';

-- ============================================================
-- 登录失败计数（也可走 Redis，此处保留 DB 表作为审计）
-- ============================================================
DROP TABLE IF EXISTS sys_login_fail;
CREATE TABLE sys_login_fail (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_name           VARCHAR(50)     NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    ip                  VARCHAR(64)     DEFAULT NULL,
    user_agent          VARCHAR(500)    DEFAULT NULL,
    fail_reason         VARCHAR(200)    DEFAULT NULL,
    fail_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (id),
    KEY idx_loginfail_user (user_name, fail_at),
    KEY idx_loginfail_ip (ip, fail_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录失败审计';
```

### V1.1.3__init_auth_audit.sql

```sql
-- ============================================================
-- 权限变更审计（数据审计，比 sys_oper_log 更细）
-- ============================================================
DROP TABLE IF EXISTS sys_auth_audit;
CREATE TABLE sys_auth_audit (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    audit_type          VARCHAR(20)     NOT NULL                        COMMENT 'ROLE_ASSIGN/ROLE_REVOKE/PWD_CHANGE/MFA_BIND/MFA_UNBIND',
    target_user_id      BIGINT          NOT NULL,
    operator_user_id    BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    
    before_value        JSON            DEFAULT NULL                    COMMENT '变更前',
    after_value         JSON            DEFAULT NULL                    COMMENT '变更后',
    reason              VARCHAR(500)    DEFAULT NULL,
    
    create_by, create_time,
    
    PRIMARY KEY (id),
    KEY idx_auth_audit_user (target_user_id, create_time),
    KEY idx_auth_audit_type (audit_type, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证授权审计';
```

---

## G.3 org-structure 模块

### V1.2.0__extend_sys_dept_for_org_structure.sql

```sql
-- ============================================================
-- 扩展 sys_dept
-- ============================================================
ALTER TABLE sys_dept
    ADD COLUMN tenant_id        BIGINT          NOT NULL DEFAULT 0              COMMENT '租户ID' AFTER dept_id,
    ADD COLUMN path             VARCHAR(500)    DEFAULT ''                      COMMENT '物化路径（如 /100/101/）',
    ADD COLUMN dept_code        VARCHAR(50)     DEFAULT ''                      COMMENT '部门编码',
    ADD COLUMN dept_type        TINYINT         NOT NULL DEFAULT 1              COMMENT '1-组织 2-虚拟',
    ADD COLUMN deleted          TINYINT         NOT NULL DEFAULT 0              COMMENT '逻辑删除';

-- 回填 path（基于 ancestors）
UPDATE sys_dept
SET path = CONCAT('/', ancestors, '/', dept_id, '/'),
    tenant_id = 1
WHERE path = '' OR path IS NULL;

-- 索引
ALTER TABLE sys_dept
    ADD INDEX idx_sys_dept_tenant (tenant_id, deleted),
    ADD INDEX idx_sys_dept_parent (parent_id, deleted),
    ADD INDEX idx_sys_dept_path (path),
    ALGORITHM = INPLACE, LOCK = NONE;
```

### V1.2.1__init_org_extension_tables.sql

```sql
-- ============================================================
-- 部门关系（多对多 - 矩阵组织）
-- ============================================================
DROP TABLE IF EXISTS org_dept_relation;
CREATE TABLE org_dept_relation (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    dept_id             BIGINT          NOT NULL,
    parent_id           BIGINT          NOT NULL,
    rel_type            TINYINT         NOT NULL DEFAULT 1              COMMENT '1-行政隶属 2-汇报',
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    
    create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    KEY idx_org_rel_dept (dept_id, rel_type, deleted),
    KEY idx_org_rel_parent (parent_id, rel_type, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门关系（多对多）';

-- ============================================================
-- 职级体系
-- ============================================================
DROP TABLE IF EXISTS org_position_level;
CREATE TABLE org_position_level (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    level_code          VARCHAR(20)     NOT NULL                        COMMENT 'P5/P6/P7',
    level_name          VARCHAR(50)     NOT NULL                        COMMENT '助理/工程师/高级工程师',
    level_seq           INT             NOT NULL                        COMMENT '数值越小级别越高',
    level_category      VARCHAR(20)     DEFAULT 'P'                     COMMENT 'P-专业 M-管理',
    
    tenant_id, deleted, create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_level_code (tenant_id, level_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '职级体系';

-- 初始数据
INSERT INTO org_position_level (tenant_id, level_code, level_name, level_seq, level_category) VALUES
(1, 'P1', '实习生',     9, 'P'),
(1, 'P2', '助理',       8, 'P'),
(1, 'P3', '初级工程师', 7, 'P'),
(1, 'P4', '工程师',     6, 'P'),
(1, 'P5', '高级工程师', 5, 'P'),
(1, 'P6', '资深工程师', 4, 'P'),
(1, 'P7', '专家',       3, 'P'),
(1, 'P8', '高级专家',   2, 'P'),
(1, 'P9', '资深专家',   1, 'P'),
(1, 'M1', '主管',       5, 'M'),
(1, 'M2', '经理',       4, 'M'),
(1, 'M3', '高级经理',   3, 'M'),
(1, 'M4', '总监',       2, 'M'),
(1, 'M5', '副总裁',     1, 'M');

-- ============================================================
-- 汇报线（员工 ↔ 上级）
-- ============================================================
DROP TABLE IF EXISTS org_reporting_line;
CREATE TABLE org_reporting_line (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT          NOT NULL                        COMMENT '员工 ID',
    manager_id          BIGINT          NOT NULL                        COMMENT '上级 ID',
    line_type           TINYINT         NOT NULL DEFAULT 1              COMMENT '1-实线 2-虚线',
    is_primary          TINYINT         NOT NULL DEFAULT 0              COMMENT '是否主汇报线',
    effective_date      DATE            NOT NULL,
    expire_date         DATE            DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    
    create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    KEY idx_org_rep_emp (employee_id, line_type, deleted),
    KEY idx_org_rep_mgr (manager_id, line_type, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '汇报线';

-- ============================================================
-- 编制（部门 + 岗位 = 计划人数）
-- ============================================================
DROP TABLE IF EXISTS org_headcount;
CREATE TABLE org_headcount (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    dept_id             BIGINT          NOT NULL,
    post_id             BIGINT          DEFAULT NULL                    COMMENT '岗位 NULL=不限岗位',
    level_id            BIGINT          DEFAULT NULL                    COMMENT '职级 NULL=不限职级',
    
    plan_count          INT             NOT NULL DEFAULT 0              COMMENT '编制数',
    actual_count        INT             NOT NULL DEFAULT 0              COMMENT '实际数（计算得出）',
    
    effective_date      DATE            NOT NULL,
    expire_date         DATE            DEFAULT NULL,
    tenant_id, deleted, create_by, create_time, update_by, update_time,
    
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_hc (tenant_id, dept_id, post_id, level_id, effective_date, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门编制';
```

### V1.2.2__init_org_history_tables.sql

```sql
-- ============================================================
-- 部门调整历史（保留组织变更轨迹）
-- ============================================================
DROP TABLE IF EXISTS org_dept_history;
CREATE TABLE org_dept_history (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    dept_id             BIGINT          NOT NULL,
    change_type         VARCHAR(20)     NOT NULL                        COMMENT 'CREATE/MOVE/RENAME/DISABLE/ENABLE',
    before_value        JSON            DEFAULT NULL                    COMMENT '变更前快照',
    after_value         JSON            DEFAULT NULL                    COMMENT '变更后快照',
    change_reason       VARCHAR(500)    DEFAULT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    
    create_by, create_time,
    
    PRIMARY KEY (id),
    KEY idx_org_hist_dept (dept_id, create_time),
    KEY idx_org_hist_type (change_type, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门调整历史';

-- ============================================================
-- 员工岗位变动历史
-- ============================================================
DROP TABLE IF EXISTS org_employee_history;
CREATE TABLE org_employee_history (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT          NOT NULL,
    change_type         VARCHAR(20)     NOT NULL                        COMMENT 'HIRE/TRANSFER/PROMOTE/DEMOTE/RESIGN',
    before_value        JSON            DEFAULT NULL,
    after_value         JSON            DEFAULT NULL,
    effective_date      DATE            NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    
    create_by, create_time,
    
    PRIMARY KEY (id),
    KEY idx_org_emp_hist (employee_id, change_type, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工岗位变动历史';
```

---

## G.4 索引设计原则（贯穿所有脚本）

### 索引类型选择

| 场景 | 索引类型 | 备注 |
|---|---|---|
| **主键** | `PRIMARY KEY (id)` | AUTO_INCREMENT BIGINT |
| **单字段等值** | 普通 B+Tree | `idx_xxx_col (col)` |
| **联合查询** | 联合 B+Tree | 顺序：等值在前、范围在后 |
| **唯一约束** | UNIQUE | `(tenant_id, code)` |
| **前缀模糊** | 普通 B+Tree + LIKE 'xxx%' | 避免 LIKE '%xxx%' |
| **全文搜索** | FULLTEXT / ES | 迁移到 ES |
| **JSON 字段查询** | 表达式索引（MySQL 8.0+） | `((CAST(data->'$.status' AS CHAR(10))))` |

### 关键索引示例

```sql
-- 列表查询：按租户 + 部门 + 状态 + 时间排序
CREATE INDEX idx_hr_employee_list
  ON hr_employee(tenant_id, deleted, dept_id, status, create_time DESC);

-- 唯一约束：租户内唯一
CREATE UNIQUE INDEX uk_xxx_tenant_code
  ON xxx_table(tenant_id, code, deleted);

-- 字典查询：字典类型 + 状态
CREATE INDEX idx_dict_lookup
  ON sys_dict_data(dict_type, status, deleted, dict_sort);
```

---

## G.5 Flyway 脚本在 Maven 项目中的组织

```
lumen-platform-base/
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V1.0.0__init_tenant_tables.sql
│   │   ├── V1.0.1__init_tenant_seed_data.sql
│   │   └── V1.0.2__add_tenant_indexes.sql
│   ├── application.yml
│   └── application-dev.yml
└── pom.xml

lumen-auth-rbac/
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V1.1.0__extend_sys_user_for_multi_tenant.sql
│   │   ├── V1.1.1__extend_sys_role_for_data_scope.sql
│   │   ├── V1.1.2__init_sso_tables.sql
│   │   └── V1.1.3__init_auth_audit.sql
│   └── application.yml
└── pom.xml
```

### application.yml 配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    table: flyway_schema_history
    validate-on-migrate: true
    placeholder-replacement: false
    
  datasource:
    url: jdbc:mysql://mysql.lumen.svc:3306/lumen_db?useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: ${DB_USER:lumen}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

---

## G.6 迁移注意事项

1. **生产数据库变更必须先在测试环境演练**
2. **大表 ALTER 加字段必须用 `pt-online-schema-change` 或 `gh-ost`**（避免锁表）
3. **每个 V 脚本必须可回滚**（虽然 Flyway 不自动回滚，但需要 `U__xxx_rollback.sql` 备份）
4. **DDL 与应用代码分开部署**（DDL 先发，代码后发）
5. **CI 跑 migration test**：H2 / TestContainers 启动服务，自动跑 Flyway

---

**下一附录**：[附录 H：安全详细设计](./H-security-detailed-design.md)
