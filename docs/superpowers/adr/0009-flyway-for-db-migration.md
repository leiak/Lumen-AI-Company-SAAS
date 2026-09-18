# ADR-0009: 数据库迁移 Flyway

## 状态
2026-09-18 已决策

## 背景
多团队并行开发，DDL 变更需要版本化管理，避免冲突。

## 决策
**Flyway 9.x** 作为数据库迁移工具。

## 备选方案

| 方案 | 优点 | 缺点 | 决策 |
|---|---|---|---|
| **Flyway（✅）** | 轻量、纯 Java、与 Spring Boot 集成好、SQL 文件直观 | Java 生态专属 | ✅ |
| **Liquibase** | 支持 XML/YAML/SQL 多种格式、跨 DB | 学习成本高、配置复杂 | ❌ |
| **手写 init.sql + 维护脚本** | 最简 | 团队协作易冲突、版本难追踪 | ❌ |
| **k8s Job 跑迁移** | 云原生 | 与应用启动耦合差 | ❌ |

## 命名规范

```
src/main/resources/db/migration/
├── V1.0.0__init_sys_tables.sql
├── V1.0.1__init_hr_tables.sql
├── V1.1.0__add_hr_employee_id_card.sql
├── V1.1.1__add_fin_voucher_index.sql
└── V2.0.0__migrate_workflow_to_v2.sql
```

格式：`V{版本}__{描述}.sql`，双下划线分隔。

## 关键纪律

### ✅ DO
- 所有 DDL 必须走 Flyway 脚本
- 只增不改不删（expand → migrate → contract 三步法）
- 大表加字段必须 `NULL` 默认值
- 索引创建用 `ALGORITHM=INPLACE, LOCK=NONE`（避免锁表）
- 版本号严格递增
- 与代码一起进 Git

### ❌ DON'T
- ❌ 手动改生产库 DDL
- ❌ 删除旧脚本
- ❌ 在事务里执行 DDL（MySQL 不支持事务 DDL）
- ❌ 在脚本里写 INSERT/UPDATE（数据迁移用 `R__` repeatable 脚本或代码任务）

## 灰度发布与 DDL 的协同

```
1. expand: 新字段允许 NULL，旧代码继续运行
2. 代码同时支持新旧两套字段（双写）
3. 灰度切读：新代码上线，老代码降级
4. 灰度切写：完全切到新代码
5. contract: 删除旧字段（确认无引用后）
```

## 失败处理

| 失败场景 | 处理 |
|---|---|
| 脚本语法错误 | Flyway 启动失败 → 回滚版本号 → 修复 |
| 脚本执行中途失败 | Flyway 锁住后续版本，需手动 `flyway repair` |
| 生产已应用版本与 dev 不一致 | 紧急修复脚本（V hotfix） |

## 后果

### 优点
- DDL 与代码版本绑定，部署一致性
- 启动自动校验，杜绝漏脚本
- 与 CI/CD 集成（每次 PR 自动跑 migration test）

### 缺点
- Flyway 不支持回滚（需手写 `U{version}__rollback.sql`，但官方不推荐）
- 大表 DDL 仍可能锁表（需 DBA 配合）
