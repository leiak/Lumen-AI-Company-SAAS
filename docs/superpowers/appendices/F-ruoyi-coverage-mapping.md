# 附录 F：RuoYi 覆盖映射表

> 本附录是**改造 RuoYi-Cloud 的精确指南**。把 RuoYi 现有 19 张 `sys_*` + 2 张 `gen_*` 表逐张标出：
> - **继承**：完全保留，不动
> - **扩展**：保留 + 加字段（业务需要）
> - **替换**：用新表替换 RuoYi 表
> - **新增**：全新建表
>
> 帮助团队在改造时**精确到字段**，避免遗漏或冲突。

---

## F.1 映射总览

| 编号 | RuoYi 表 | 模块 | 改造方式 | 关键变化 | 文档位置 |
|---|---|---|---|---|---|
| 1 | `sys_dept` | org-structure | **扩展** | 适配多租户；增加 `path` 物化路径、`tenant_id` | [F.2](#f2-sys_dept-部门表) |
| 2 | `sys_user` | auth-rbac | **扩展** | 加 `tenant_id`、`data_scope`、`mfa_secret`、`pwd_expire_at`、`id_card_enc`、`mobile_enc` | [F.3](#f3-sys_user-用户表) |
| 3 | `sys_post` | org-structure | **继承** | 不变（岗位概念复用） | [F.4](#f4-sys_post-岗位表) |
| 4 | `sys_role` | auth-rbac | **扩展** | 加 `tenant_id`、`data_scope`（改用 TINYINT）、`api_pattern` | [F.5](#f5-sys_role-角色表) |
| 5 | `sys_menu` | system-mgmt | **扩展** | 加 `tenant_id`、`api_pattern`、`i18n_key` | [F.6](#f6-sys_menu-菜单表) |
| 6 | `sys_user_role` | auth-rbac | **继承** | 不变 | [F.7](#f7-sys_user_role-用户角色) |
| 7 | `sys_role_menu` | auth-rbac | **继承** | 不变 | [F.8](#f8-sys_role_menu-角色菜单) |
| 8 | `sys_role_dept` | auth-rbac | **继承** | 不变（与 data_scope=5 自定义配合） | [F.9](#f9-sys_role_dept-角色部门) |
| 9 | `sys_user_post` | org-structure | **继承** | 不变 | [F.10](#f10-sys_user_post-用户岗位) |
| 10 | `sys_oper_log` | system-mgmt | **扩展** | 加 `tenant_id`、`trace_id`、`cost_ms` | [F.11](#f11-sys_oper_log-操作日志) |
| 11 | `sys_dict_type` | system-mgmt | **继承** | 不变（字典全局共享） | [F.12](#f12-sys_dict_type字典类型) |
| 12 | `sys_dict_data` | system-mgmt | **继承** | 不变 | [F.13](#f13-sys_dict_data字典数据) |
| 13 | `sys_config` | system-mgmt | **扩展** | 加 `tenant_id`（部分参数租户级） | [F.14](#f14-sys_config-参数配置) |
| 14 | `sys_logininfor` | system-mgmt | **扩展** | 加 `tenant_id`、`user_id`（关联用户）、`ua` | [F.15](#f15-sys_logininfor-登录日志) |
| 15 | `sys_job` | system-mgmt | **继承** | 不变（XXL-JOB 替换后可能废弃） | [F.16](#f16-sys_job-定时任务) |
| 16 | `sys_job_log` | system-mgmt | **继承** | 不变 | [F.17](#f17-sys_job_log-任务日志) |
| 17 | `sys_notice` | message-center | **扩展** | 加 `tenant_id`、`send_channel`、`receiver_type`、`publish_time` | [F.18](#f18-sys_notice-通知公告) |
| 18 | `sys_notice_read` | message-center | **继承** | 不变 | [F.19](#f19-sys_notice_read-公告已读) |
| 19 | `gen_table` | system-mgmt | **继承** | 不变（代码生成器底层） | [F.20](#f20-gen_table-代码生成业务表) |
| 20 | `gen_table_column` | system-mgmt | **继承** | 不变 | [F.21](#f21-gen_table_column-代码生成字段) |

**新增表**（RuoYi 中不存在）：见 [F.22 新增表清单](#f22-新增表清单)

---

## F.2 sys_dept（部门表）

**改造方式**：扩展

### RuoYi 现状
```sql
sys_dept (dept_id, parent_id, ancestors, dept_name, order_num,
          leader, phone, email, status, del_flag,
          create_by, create_time, update_by, update_time)
```

### 改造后
```sql
ALTER TABLE sys_dept
  ADD COLUMN tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN path             VARCHAR(500) DEFAULT ''  COMMENT '物化路径 /100/101/103/',
  ADD COLUMN dept_code        VARCHAR(50)  DEFAULT ''  COMMENT '部门编码',
  ADD COLUMN dept_type        TINYINT      DEFAULT 1   COMMENT '1-组织 2-虚拟',
  ADD COLUMN deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除';

CREATE INDEX idx_sys_dept_tenant_id ON sys_dept(tenant_id);
CREATE INDEX idx_sys_dept_parent_id ON sys_dept(parent_id);
CREATE INDEX idx_sys_dept_path ON sys_dept(path);
```

### 关键变化
| 字段 | 变化 | 说明 |
|---|---|---|
| `tenant_id` | **新增** | 多租户隔离 |
| `path` | **新增**（RuoYi 有 ancestors，保留兼容） | 物化路径，5 级以上部门递归查询 O(1) |
| `dept_code` | **新增** | 部门编码（外部系统对接用） |
| `dept_type` | **新增** | 区分组织和虚拟团队 |
| `del_flag` | 保留 → 改名为 `deleted` | 统一字段命名（约定） |

### 配套新增表
```sql
CREATE TABLE org_dept_relation (
  rel_id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id     BIGINT NOT NULL,
  parent_id   BIGINT NOT NULL,
  rel_type    TINYINT DEFAULT 1 COMMENT '1-组织 2-汇报',
  tenant_id   BIGINT NOT NULL,
  deleted     TINYINT NOT NULL DEFAULT 0,
  create_by   BIGINT, create_time DATETIME,
  update_by   BIGINT, update_time DATETIME,
  KEY idx_org_dept_rel_dept (dept_id)
) COMMENT '部门关系（多对多，矩阵组织）';
```

---

## F.3 sys_user（用户表）

**改造方式**：扩展（**最关键**的一张表）

### 改造后
```sql
ALTER TABLE sys_user
  ADD COLUMN tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN data_scope       TINYINT      NOT NULL DEFAULT 1 COMMENT '数据权限 1-5',
  ADD COLUMN mfa_secret       VARCHAR(100) DEFAULT NULL COMMENT 'TOTP 密钥',
  ADD COLUMN mfa_enabled      TINYINT      NOT NULL DEFAULT 0 COMMENT 'MFA 是否开启',
  ADD COLUMN pwd_expire_at    DATETIME     DEFAULT NULL COMMENT '密码过期时间',
  ADD COLUMN pwd_history      VARCHAR(500) DEFAULT ''  COMMENT '历史密码哈希（5 个，逗号分隔）',
  ADD COLUMN id_card_enc      VARCHAR(500) DEFAULT NULL COMMENT '身份证 AES-256-GCM 加密',
  ADD COLUMN mobile_enc       VARCHAR(500) DEFAULT NULL COMMENT '手机号加密',
  ADD COLUMN email_enc        VARCHAR(500) DEFAULT NULL COMMENT '邮箱加密',
  ADD COLUMN bank_card_enc    VARCHAR(500) DEFAULT NULL COMMENT '银行卡加密',
  ADD COLUMN last_pwd_change  DATETIME     DEFAULT NULL COMMENT '上次改密时间',
  ADD COLUMN fail_count       INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  ADD COLUMN lock_until       DATETIME     DEFAULT NULL COMMENT '锁定至此时刻',
  ADD COLUMN deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除';

CREATE INDEX idx_sys_user_tenant ON sys_user(tenant_id);
CREATE INDEX idx_sys_user_dept ON sys_user(dept_id);
CREATE UNIQUE INDEX uk_sys_user_tenant_username ON sys_user(tenant_id, user_name);
```

### 关键变化
| 字段 | 变化 | 说明 |
|---|---|---|
| `tenant_id` | **新增** | 多租户隔离 |
| `data_scope` | **新增** | 与 `sys_role.data_scope` 一致（用户级覆盖） |
| `mfa_secret/mfa_enabled` | **新增** | MFA 双因素 |
| `pwd_expire_at/pwd_history` | **新增** | 密码策略 |
| `*_enc` | **新增** | 敏感字段加密 |
| `fail_count/lock_until` | **新增** | 登录失败锁定 |
| `del_flag` | **改名为 `deleted`** | 统一字段命名 |
| `uk_sys_user_username` | **改** | 单字段唯一 → `(tenant_id, user_name)` 联合唯一 |

### 配套新增表
```sql
CREATE TABLE sys_sso_account (
  sso_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT NOT NULL,
  platform      VARCHAR(20) NOT NULL COMMENT 'dingtalk/wechatwork/feishu',
  open_id       VARCHAR(100) NOT NULL,
  union_id      VARCHAR(100),
  tenant_id     BIGINT NOT NULL,
  bind_at       DATETIME,
  create_by, create_time, update_by, update_time,
  UNIQUE KEY uk_sso_platform_open (platform, open_id)
) COMMENT 'SSO 账号绑定';

CREATE TABLE sys_user_session (
  session_id    VARCHAR(64) PRIMARY KEY COMMENT 'Session ID = tokenId',
  user_id       BIGINT NOT NULL,
  tenant_id     BIGINT NOT NULL,
  refresh_token VARCHAR(500),
  ip            VARCHAR(64),
  ua            VARCHAR(500),
  login_at      DATETIME,
  expire_at     DATETIME NOT NULL,
  KEY idx_session_user (user_id),
  KEY idx_session_expire (expire_at)
) COMMENT '会话表（用于 Refresh Token 撤销）';
```

---

## F.4 sys_post（岗位表）

**改造方式**：继承（**不动**）

```sql
-- RuoYi 现状完全保留
sys_post (post_id, post_code, post_name, post_sort, status,
          create_by, create_time, update_by, update_time, remark)
```

岗位是 RuoYi 设计得最干净的表，直接复用。

---

## F.5 sys_role（角色表）

**改造方式**：扩展

### 改造后
```sql
ALTER TABLE sys_role
  ADD COLUMN tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN api_pattern      VARCHAR(500) DEFAULT NULL COMMENT 'API 路径匹配（Ant 风格）',
  ADD COLUMN i18n_key         VARCHAR(100) DEFAULT NULL COMMENT '国际化 key',
  CHANGE COLUMN data_scope     data_scope   TINYINT NOT NULL DEFAULT 1 COMMENT '1-全部 2-本部门 3-本部门及下级 4-本人 5-自定义';

CREATE INDEX idx_sys_role_tenant ON sys_role(tenant_id);
CREATE UNIQUE INDEX uk_sys_role_tenant_key ON sys_role(tenant_id, role_key);
```

### 关键变化
| 字段 | 变化 | 说明 |
|---|---|---|
| `tenant_id` | **新增** | 多租户隔离 |
| `data_scope` | **类型改 `char(1)` → `TINYINT`** | 支持数字 1-5（原本是字符'1'-'4'） |
| `api_pattern` | **新增** | 角色可绑定 API 路径模式 |
| `i18n_key` | **新增** | 国际化 |
| `uk_sys_role_key` | **改** | 单字段唯一 → `(tenant_id, role_key)` |

---

## F.6 sys_menu（菜单表）

**改造方式**：扩展

```sql
ALTER TABLE sys_menu
  ADD COLUMN tenant_id     BIGINT       DEFAULT NULL COMMENT 'NULL=系统内置，值=租户自定义',
  ADD COLUMN api_pattern   VARCHAR(500) DEFAULT NULL COMMENT 'API 路径',
  ADD COLUMN i18n_key      VARCHAR(100) DEFAULT NULL COMMENT '国际化 key';

CREATE INDEX idx_sys_menu_tenant ON sys_menu(tenant_id);
```

### 关键变化
- `tenant_id` 可为 NULL（系统内置菜单）或具体值（租户自定义菜单）
- 增加 `api_pattern` 用于按钮级 API 权限控制

---

## F.7 sys_user_role（用户-角色）

**改造方式**：继承

**注意**：组合主键改为 `(user_id, role_id)` 不变。如果将来需要加审计字段（创建时间），可用新表 `sys_user_role_history`。

---

## F.8 sys_role_menu（角色-菜单）

**改造方式**：继承

---

## F.9 sys_role_dept（角色-部门）

**改造方式**：继承

- 与 `sys_role.data_scope = 5 (自定义)` 配合
- 用户用该角色时只能看 `sys_role_dept` 关联的部门数据

---

## F.10 sys_user_post（用户-岗位）

**改造方式**：继承

---

## F.11 sys_oper_log（操作日志）

**改造方式**：扩展

```sql
ALTER TABLE sys_oper_log
  ADD COLUMN tenant_id   BIGINT      DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN trace_id    VARCHAR(64) DEFAULT NULL COMMENT 'SkyWalking traceId',
  ADD COLUMN cost_ms     BIGINT      DEFAULT 0 COMMENT '耗时 ms',
  ADD COLUMN user_id     BIGINT      DEFAULT NULL COMMENT '用户ID（除名称外冗余）';

CREATE INDEX idx_sys_oper_log_tenant ON sys_oper_log(tenant_id);
CREATE INDEX idx_sys_oper_log_user ON sys_oper_log(oper_name);
CREATE INDEX idx_sys_oper_log_time ON sys_oper_log(oper_time);
```

---

## F.12 sys_dict_type（字典类型）

**改造方式**：继承

**注意**：字典是全局共享的，**不**加 `tenant_id`。`@IgnoreTenant` 显式标注。

---

## F.13 sys_dict_data（字典数据）

**改造方式**：继承

同样不加 `tenant_id`，全局共享。

---

## F.14 sys_config（参数配置）

**改造方式**：扩展

```sql
ALTER TABLE sys_config
  ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT 'NULL=全局，值=租户级';
```

RuoYi 的 `sys_config` 主要是系统级（如"是否开启注册"、"默认皮肤"），业务级参数我们走 `tenant_config` 新表。

---

## F.15 sys_logininfor（登录日志）

**改造方式**：扩展

```sql
ALTER TABLE sys_logininfor
  ADD COLUMN tenant_id  BIGINT       DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN user_id    BIGINT       DEFAULT NULL COMMENT '用户ID',
  ADD COLUMN ua         VARCHAR(500) DEFAULT '' COMMENT 'User-Agent';

CREATE INDEX idx_logininfor_tenant ON sys_logininfor(tenant_id);
```

---

## F.16 sys_job（定时任务）

**改造方式**：继承 + **逐步替换**

**注意**：RuoYi 自带的 `sys_job` 用的是 Quartz。**生产推荐替换为 XXL-JOB**（更强大、更易用）。
- Phase 0：保留 RuoYi 的 Quartz 实现
- Phase 4 前：迁移到 XXL-JOB（用 `xxl_job_info` + `xxl_job_log` 替换 `sys_job` + `sys_job_log`）

---

## F.17 sys_job_log（任务日志）

**改造方式**：继承

同 F.16，Phase 4 前替换为 XXL-JOB。

---

## F.18 sys_notice（通知公告）

**改造方式**：扩展

```sql
ALTER TABLE sys_notice
  ADD COLUMN tenant_id       BIGINT      NOT NULL DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN send_channel    VARCHAR(50) DEFAULT 'INNER' COMMENT 'INNER/EMAIL/SMS/DINGTALK',
  ADD COLUMN receiver_type   VARCHAR(20) DEFAULT 'ALL' COMMENT 'ALL/ROLE/USER',
  ADD COLUMN receiver_ids    VARCHAR(2000) DEFAULT NULL COMMENT '接收人ID列表',
  ADD COLUMN publish_time    DATETIME    DEFAULT NULL COMMENT '发布时间',
  ADD COLUMN publisher_id    BIGINT      DEFAULT NULL;

CREATE INDEX idx_sys_notice_tenant ON sys_notice(tenant_id);
```

---

## F.19 sys_notice_read（公告已读）

**改造方式**：继承

---

## F.20 gen_table（代码生成业务表）

**改造方式**：继承

代码生成器底层。

---

## F.21 gen_table_column（代码生成字段）

**改造方式**：继承

---

## F.22 新增表清单

RuoYi 中**不存在**，本系统新增：

### 平台层

| 表 | 所属模块 | 用途 |
|---|---|---|
| `tenant` | platform-base | 租户主表 |
| `tenant_package` | platform-base | 套餐 |
| `tenant_config` | platform-base | 租户配置 |
| `common_seq` | platform-base | 业务序列号生成器 |
| `lumen_application` | platform-base | 微服务应用注册表（替代 RuoYi 的手写） |

### 工作流层

| 表 | 所属模块 | 用途 |
|---|---|---|
| `wf_definition` | workflow-engine | 流程定义 |
| `wf_instance` | workflow-engine | 流程实例 |
| `wf_task` | workflow-engine | 任务 |
| `wf_task_history` | workflow-engine | 任务历史 |
| `wf_cc_record` | workflow-engine | 抄送 |
| `wf_form_schema` | workflow-engine | 表单 Schema |

### 消息层

| 表 | 所属模块 | 用途 |
|---|---|---|
| `msg_template` | message-center | 消息模板 |
| `msg_log` | message-center | 发送日志 |
| `msg_subscription` | message-center | 订阅 |

### 文件层

| 表 | 所属模块 | 用途 |
|---|---|---|
| `file_info` | file-storage | 文件元数据 |
| `file_share` | file-storage | 文件分享 |
| `file_chunk` | file-storage | 分片上传 |

### 业务层

| 表 | 所属模块 | 用途 |
|---|---|---|
| `hr_*`（11 张） | hr | 人力资源 |
| `fin_*`（11 张） | finance | 财务 |
| `ast_*`（10 张） | assets | 资产 |
| `proc_*`（9 张） | procurement | 采购 |
| `ctr_*`（8 张） | contract | 合同 |
| `inv_*`（9 张） | inventory | 库存 |
| `sal_*`（11 张） | sales-crm | 销售/CRM |
| `pay_*`（8 张） | payroll | 薪资 |
| `bi_*`（6 张） | bi-reporting | BI 数仓 |

合计新增：~100 张业务表（详见附录 A）。

---

## F.23 Flyway 迁移脚本设计

### 命名规范

```
src/main/resources/db/migration/
├── V1.0.0__init_ruoyi_tables.sql             ← RuoYi 原始表（保留）
├── V1.0.1__init_ruoyi_seed_data.sql          ← RuoYi 初始数据
├── V1.1.0__extend_sys_user_for_multi_tenant.sql
├── V1.1.1__extend_sys_role_for_data_scope.sql
├── V1.2.0__init_tenant_tables.sql
├── V1.2.1__init_workflow_tables.sql
├── V1.3.0__init_message_tables.sql
├── V1.4.0__init_file_tables.sql
├── V2.0.0__init_hr_tables.sql
├── V2.1.0__init_finance_tables.sql
...
└── V3.0.0__migrate_quartz_to_xxl_job.sql
```

### 版本号约定

| 版本段 | 含义 |
|---|---|
| `V1.0.x` | P0 基础设施（基于 RuoYi） |
| `V1.1.x` | P0 扩展（多租户、字段加密） |
| `V1.2.x` | P1 基础业务（workflow、message、file） |
| `V2.x.x` | P2 核心业务（HR/Finance/Assets） |
| `V3.x.x` | P3 业务协同（采购/合同/库存/销售） |
| `V4.x.x` | P4 高级能力（薪资/BI/移动） |

### 灰度变更原则（每个 V 脚本遵守）

```sql
-- V1.1.0__extend_sys_user_for_multi_tenant.sql
-- 1. expand：加列（允许 NULL + 默认值）
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1;
-- ... 其他扩展列

-- 2. 加索引（用 INPLACE 算法）
ALTER TABLE sys_user ADD INDEX idx_sys_user_tenant (tenant_id), ALGORITHM=INPLACE, LOCK=NONE;

-- 3. 数据回填（如果有）
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id = 0;  -- 单租户默认

-- 4. contract：在后续 V 版本删除旧字段（不要在本版本删）
```

---

## F.24 字段命名统一

### RuoYi 现状

```sql
-- 不统一（混用多种风格）
sys_user: del_flag, login_date, pwd_update_date
sys_dept: del_flag
sys_oper_log: oper_id, oper_time, status
```

### 改造后统一

| 旧字段 | 新字段 | 说明 |
|---|---|---|
| `del_flag` | `deleted` | 统一逻辑删除字段 |
| `login_date` | `last_login_at` | 时间类统一 `xxx_at` |
| `pwd_update_date` | `pwd_update_at` | 同上 |
| `oper_time` | `operated_at` | 同上 |
| `oper_id` | `id` | 主键统一 `id` |
| `oper_url` | `oper_uri` | URL → URI |
| `oper_ip` | `ip_address` | 命名规范化 |

### 改造方式

```sql
-- V1.1.2__rename_fields_for_naming_consistency.sql
ALTER TABLE sys_user
  CHANGE COLUMN del_flag      deleted     TINYINT      NOT NULL DEFAULT 0,
  CHANGE COLUMN login_date    last_login_at  DATETIME;
```

**注意**：RuoYi 代码里直接用了 `del_flag`，改名需同步修改 Java 代码。用 `@Deprecated` 标记过渡期，新代码用 `deleted`。

---

## F.25 RuoYi 改造工作量评估

| 类别 | 表数 | 工时 |
|---|---|---|
| 字段扩展（加列） | 8 张 | 2 人天 |
| 字段重命名 | 6 张 | 1 人天 |
| 索引重建 | 12 张 | 1 人天 |
| Flyway 迁移 | 全库 | 1 人天 |
| RuoYi Java 代码同步修改 | 涉及 sys_user/dept/role 等 | 3-5 人天 |
| 新增 tenant 拦截器 | - | 2 人天 |
| 单元测试 | - | 2 人天 |
| **小计** | | **约 12-15 人天** |

预计在 `platform-base` 子项目（第 1-3 周）完成。

---

## F.26 不推荐覆盖的 RuoYi 表

| 表 | 不覆盖原因 |
|---|---|
| `qrtz_*`（Quartz 表） | 完整替换为 XXL-JOB |
| `gen_table*` | 代码生成器底层，保留但加少量字段 |

---

## F.27 注意事项

1. **RuoYi `del_flag` 用 `char(1)`**，本系统统一 `deleted TINYINT`，需脚本转换
2. **RuoYi `status` 用 `char(1)`**（'0'/'1'），本系统统一 `TINYINT`（0/1）
3. **RuoYi `create_by/update_by` 是 `varchar(64)`** 存用户名，本系统改为 `BIGINT` 存 user_id（关联 `sys_user.id`），username 通过 JOIN 获取
4. **RuoYi 没有租户概念**，所有 `tenant_id` 字段都是新增，需要回填（默认 0）
5. **RuoYi 默认密码 `123456`**，本系统强制首次登录修改

---

**下一附录**：[附录 G：DB Schema 设计示例](./G-db-schema-examples.md)
