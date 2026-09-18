# Lumen AI Company Manage — 顶层架构设计 Spec

> 版本：v1.1  
> 日期：2026-09-18  
> 状态：✅ 用户已批准（设计中）  
> 范围：顶层架构与共用规范；不包含单个业务模块的详细设计（每个子项目单独 spec）
>
> **本 spec 由以下子文档支撑**（务必配合阅读）：
>
> | 文档 | 路径 | 说明 |
> |---|---|---|
> | 附录 A | `appendices/A-17-subprojects-overview.md` | 17 个子项目详细概览（目标/场景/实体/接口/事件/验收） |
> | 附录 B | `appendices/B-event-flow-diagrams.md` | 8 个核心场景的端到端时序图 |
> | 附录 C | `appendices/C-nfr-sla-matrix.md` | NFR + SLA 矩阵 + 容量规划 |
> | 附录 D | `appendices/D-glossary.md` | 术语表 + 缩写对照 |
> | 附录 E | `appendices/E-data-flow-example.md` | 数据流全景示例（一个 HTTP 请求全链路） |
> | 附录 F | `appendices/F-ruoyi-coverage-mapping.md` | RuoYi 19 张 sys_*/2 张 gen_* 表覆盖映射（继承/扩展/替换/新增） |
> | 附录 G | `appendices/G-db-schema-examples.md` | 3 个基础模块完整 Flyway 脚本（platform-base + auth-rbac + org-structure） |
> | 附录 H | `appendices/H-security-detailed-design.md` | 等保三级五大要求逐条对应 + 安全测试用例 + 渗透测试清单 |
> | ADR-0001~0015 | `adr/0001-*.md` ~ `0015-*.md` | 15 份关键架构决策记录 |

---

## 0. 文档元信息

| 项 | 内容 |
|---|---|
| 项目名 | `lumen-ai-company-manage`（基于 RuoYi-Cloud） |
| 文档类型 | 顶层架构设计 / Top-Level Architecture Design |
| 目标读者 | 架构师、技术负责人、新入职开发者 |
| 关联文档 | `docs/adr/*`（架构决策记录）、各子项目 design.md |

---

## 1. 项目背景与目标

### 1.1 背景

需要构建一套完整的企业经营管理系统，覆盖 **人力、财务、合同、采购、资产** 等核心业务，
以及完整的协作与决策能力（OA、审批、BI、移动端）。目标是打造一套**对标用友/金蝶/SAP 的国产化 ERP**，
既能私有部署，也能 SaaS 化运营。

### 1.2 目标

- ✅ 17 个核心业务模块全自研覆盖（人力/财务/合同/采购/资产/库存/销售/CRM/薪资/BI 等）
- ✅ 同时支持**私有部署**与**SaaS 多租户**两种模式（同代码双模式）
- ✅ 等保三级合规
- ✅ 支持 PC Web + H5 + 原生 App + 钉钉/企微/飞书生态
- ✅ 可对接电子签章、金税、银行等关键第三方

### 1.3 非目标（明确不做）

- ❌ 不做生产制造（MES）模块
- ❌ 不做电商前台（C 端商城）
- ❌ 不做财务总账的复杂行业版本（保险/银行专版）
- ❌ 初期不做区块链存证、不做 AI 大模型深度集成（预留接口）

---

## 2. 已确认的架构决策

| 维度 | 决策 | 备注 |
|---|---|---|
| 底座框架 | RuoYi-Cloud (SpringBoot3 + Vue3 + TS) | 用户已有 `RuoYi-Cloud-springboot3.zip`、`RuoYi-Cloud-Vue3-typescript.zip` |
| 部署模式 | **混合：私有 + SaaS 都支持**（同代码双模式） | 行级隔离起步，可升级到库级 |
| 目标规模 | 中型企业 500-3000 人 | 性能/拆分粒度按此基线 |
| 合规 | 等保三级 | 含审计、加密、双因素、敏感数据保护 |
| 数据库 | **单库 `lumen_db`**（MySQL 8.0） | BI 数仓独立 `lumen_bi_db`；表前缀严格按业务域 |
| 第三方集成 | 协作平台 + 电子签章 + 金税/银行 + SMS/邮件/OCR/地图 | 全部走 Adapter 抽象 |
| 移动端 | 原生 App（实施时建议 Flutter/uni-app 跨端） | 详细评估在子项目 17 进行 |

---

## 3. 顶层架构图

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                            用户层（多种接入形态）                                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ PC Web   │  │ H5 响应式│  │ 原生 App │  │ 钉钉应用 │  │ 企微应用 │  │ 飞书   │ │
│  │ Vue3+TS  │  │ Vant     │  │ Flutter  │  │ 小程序   │  │ 小程序   │  │ 应用   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬────┘ │
└───────┼─────────────┼─────────────┼─────────────┼─────────────────────────┼──────┘
        │             │             │             │             │            │
        ▼             ▼             ▼             ▼             ▼            ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                       接入层（CDN + WAF + LB）                                      │
│      CDN/静态加速 →  阿里云WAF/DDoS  →  SLB/Nginx  →  Spring Cloud Gateway         │
└────────────────────────────────────────────────────────────────────────────────────┘
        │                                                                       │
        ▼                                                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                网关层（Spring Cloud Gateway + 过滤器链）                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ JWT 鉴权 │ │ 多租户   │ │ Sentinel │ │ 灰度路由 │ │ 签名校验 │ │ 请求日志 │  │
│  │ 解析     │ │ 解析     │ │ 限流/熔断│ │ 流量染色 │ │ 防重放   │ │ TraceID  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└────────────────────────────────────────────────────────────────────────────────────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          微服务层（17 个业务 + 6 个基础服务）                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  基础服务（P0）                                                            │    │
│  │  ┌────────┐ ┌────────┐ ┌──────── ┌──────── ┌────────┐ ┌────────┐      │    │
│  │  │ auth   │ │ gateway│ │ system │ │ file   │ │ message│ │ workflow│     │    │
│  │  │ 认证   │ │ 网关   │ │ 系统   │ │ 文件   │ │ 消息   │ │ 工作流 │      │    │
│  │  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘      │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  业务服务（P1-P3）                                                         │    │
│  │  org(组织) hr(人力) fin(财务) ast(资产) proc(采购) ctr(合同)             │    │
│  │  inv(库存) sal(销售) pay(薪资) bi(BI) mobile(移动端)                      │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────────────────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                     中间件层（基础设施 + 分布式能力）                                │
│  Nacos / Redis Cluster / RocketMQ / Seata / XXL-JOB / Elasticsearch /           │
│  Sentinel / MinIO / SkyWalking / ELK / DataX / Flink CDC                        │
└────────────────────────────────────────────────────────────────────────────────────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          数据层 + 数据仓库                                         │
│  ┌────────────────────────────────────┐  ┌────────────────────────────────────┐  │
│  │  业务库（MySQL 8，单库 lumen_db）   │  │  数据仓库（Doris，独立 lumen_bi_db）│  │
│  │  按业务前缀组织表（hr_*/fin_*/...）│  │  Flink CDC 实时同步 + DataX 离线   │  │
│  │  关键表加 tenant_id 列              │  │  指标体系 + 多维分析               │  │
│  └────────────────────────────────────┘  └────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────┘
        │                                       │
        ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          集成层（外部第三方）                                       │
│  协作平台（钉钉/企微/飞书） | 电子签章（法大大/e签宝/契约锁）                        │
│  金税 + 银行 API | SMS + 邮件 + OCR + 高德地图                                     │
└────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                       可观测性 + 运维保障（横切）                                  │
│   Prometheus + Grafana（指标）  |  ELK（日志）  |  SkyWalking（链路）              │
│   告警（钉钉/企微 Webhook）     |  Arthas（在线诊断）  |  Gitea + Jenkins CI/CD     │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 模块划分与依赖图

### 4.1 模块清单（按 P0→P4 阶段）

```
P0 基础设施（6 个，0 业务依赖）
├── platform-base        ▓▓▓ 框架改造：多租户/异常/幂等/响应/代码生成器
├── auth-rbac            ▓▓▓ 认证授权：用户/角色/部门/数据权限/SSO/审计
├── gateway              ▓▓▓ 统一网关：路由/限流/灰度/熔断
├── system-mgmt          ▓▓ 系统管理：字典/配置/参数/定时任务/在线用户/操作日志
├── file-storage         ▓▓ 文件存储：MinIO/OSS/S3 抽象/分片上传/预览
└── message-center       ▓▓ 消息中心：站内信/邮件/短信/钉钉/企微/WebSocket

P1 基础业务（2 个，依赖 P0）
├── workflow-engine      ▓▓▓ 通用审批引擎：流程定义/实例/待办/转办/加签/驳回
└── org-structure        ▓▓ 组织架构：部门树/岗位/职级/编制/汇报线

P2 核心业务（3 个，依赖 P0+P1）
├── hr                   ▓▓▓ 人力资源：员工档案/招聘/入职/调岗/离职/绩效/培训
├── finance              ▓▓▓ 财务管理：科目/凭证/总账/应收应付/报销/预算/发票
└── assets               ▓▓ 资产管理：固定资产/低值易耗/IT设备/车辆/印章/证照

P3 业务协同（4 个，依赖 P2）
├── procurement          ▓▓ 采购管理：供应商/询比价/招投标/采购单/收货/付款
├── contract             ▓▓▓ 合同管理：起草/审批/电子签/履约/到期/归档
├── inventory            ▓▓ 库存管理：出入库/调拨/盘点/安全库存
└── sales-crm            ▓▓ 销售 + CRM：客户/商机/报价/订单/回款/对账

P4 高级能力（3 个，依赖全部）
├── payroll              ▓▓ 薪资管理：算薪/个税/社保公积金/银行报盘
├── bi-reporting         ▓▓ BI 报表：数仓/指标体系/可视化/大屏
└── mobile-integration   ▓▓ 移动端 + 协作：App/H5/钉钉/企微/飞书集成

▓▓▓ 关键模块（核心价值）     ▓▓ 标准模块（重要）     ▓ 辅助模块（支撑）
```

### 4.2 依赖图

```
                            ┌─────────────────────┐
                            │   mobile-integration│ ← P4 最后做
                            └──────────┬──────────┘
                                       │ 依赖全部
   ┌───────────────────┐               │
   │   bi-reporting    │───────────────┤
   └─────────┬─────────┘               │
             │                         │
   ┌─────────▼─────────┐         ┌─────▼──────┐
   │     payroll       │──────── │   contract │──┐
   └─────────┬─────────┘         └─────┬──────┘  │
             │                         │         │
   ┌─────────▼───────┐  ┌──────────┐  ┌▼───────┐ │
   │  procurement    │──┤  sales   │──┤inventory│ │
   └─────────┬───────┘  └────┬─────┘  └───┬─────┘ │
             │                │            │       │
             └────┬───────────┼────────────┘       │
                  │           │                    │
        ┌─────────▼──┐  ┌─────▼──────┐  ┌────────▼─┐
        │   assets   │  │   finance  │  │    hr    │
        └─────────┬──┘  └─────┬──────┘  └────┬─────┘
                  │           │              │
                  └─────┬─────┴──────┬───────┘
                        │            │
                  ┌─────▼────┐  ┌────▼─────┐
                  │   org    │  │ workflow │
                  │ structure│  │  engine  │
                  └────┬────┘  └────┬─────┘
                       │            │
       ┌───────────────┼────────────┼───────────────┐
       │               │            │               │
   ┌───▼───┐  ┌───────▼────┐  ┌───▼────┐  ┌────────▼─────┐
   │ auth  │  │  platform   │  │ file   │  │   message    │
   │ rbac  │  │   base      │  │storage │  │   center     │
   └───┬───┘  └───────┬─────┘  └───┬────┘  └────────┬─────┘
       │              │            │                │
       └──────────────┼────────────┼────────────────┘
                      │            │
                ┌─────▼────┐  ┌────▼────┐
                │ gateway  │  │ system  │
                │          │  │  mgmt   │
                └────┬─────┘  └────┬────┘
                     │             │
                     └──────┬──────┘
                            │
                  ┌─────────▼──────────┐
                  │   中间件 + DB + 集成  │
                  └────────────────────┘
```

### 4.3 关键依赖说明

| 上游 | 下游 | 依赖内容 |
|---|---|---|
| workflow | auth, org | 审批人/角色/部门查询 |
| hr | org, workflow, message | 入职流程→审批→入职通知 |
| finance | workflow, message | 报销审批→打款通知 |
| assets | org, workflow | 资产领用→审批→通知 |
| procurement | finance, assets, workflow | 采购单→财务付款→资产入库→审批 |
| contract | workflow, file, procurement | 合同审批→文件存档→采购单联动 |
| inventory | procurement, sales | 采购入库/销售出库 |
| payroll | hr, finance | 员工薪资数据来自 HR，发放走财务 |
| bi-reporting | 全部业务库 | 抽取所有业务数据进行指标分析 |
| mobile-integration | auth, message, workflow | 复用登录/推送/审批能力 |

### 4.4 边界约定

1. **基础服务不调任何业务服务** —— auth、platform-base、workflow 只被调，不调下游
2. **业务服务之间不直接 RPC** —— 通过事件总线（RocketMQ 领域事件）异步解耦
3. **跨业务查询走 API Composer 或 BI 数仓** —— 不允许 A 服务 join B 服务的表
4. **公共能力通过 OpenFeign 调用，不嵌入业务服务** —— 单一职责

### 4.5 表前缀命名地图（统一库 `lumen_db`）

RuoYi 自带的 `sys_*` 表（`sys_user`、`sys_role`、`sys_menu`、`sys_dept`、`sys_post`、`sys_dict_*`、`sys_config`、`sys_oper_log`、`sys_logininfor`、`sys_job`、`sys_notice`、`gen_table`）**全部复用**，仅做字段增强（如 `sys_user` 加 `tenant_id`、`data_scope`）。

| 业务域 | 表前缀 | 新增关键表 |
|---|---|---|
| 系统 | `sys_*` | 复用 RuoYi |
| 租户 | `tenant_*` | `tenant`、`tenant_package`、`tenant_config` |
| 工作流 | `wf_*` | `wf_definition`、`wf_instance`、`wf_task` |
| 文件 | `file_*` | `file_info`、`file_share` |
| 消息 | `msg_*` | `msg_template`、`msg_log`（`sys_notice` 复用为站内信） |
| 组织 | `org_*` | `org_dept_relation`、`org_reporting_line`（扩展 `sys_dept`） |
| 人力 | `hr_*` | `hr_employee`、`hr_attendance`、`hr_leave`、`hr_performance` |
| 财务 | `fin_*` | `fin_account`、`fin_voucher`、`fin_invoice`、`fin_budget` |
| 资产 | `ast_*` | `ast_asset`、`ast_stocktake`、`ast_depreciation` |
| 采购 | `proc_*` | `proc_supplier`、`proc_order`、`proc_receipt` |
| 合同 | `ctr_*` | `ctr_contract`、`ctr_payment_plan`、`ctr_fulfillment` |
| 库存 | `inv_*` | `inv_stock`、`inv_inout`、`inv_transfer` |
| 销售 | `sal_*` | `sal_customer`、`sal_order`、`sal_receivable` |
| 薪资 | `pay_*` | `pay_slip`、`pay_bank_file`、`pay_tax` |
| BI | `bi_*` | `bi_dataset`、`bi_dashboard`、`bi_metric` |

---

## 5. 共用规范（14 项横切关注点）

### 5.1 命名规范

| 对象 | 规则 | 示例 |
|---|---|---|
| Java 包 | `com.lumen.{module}.{layer}` | `com.lumen.hr.service` |
| 类 | 大驼峰，名词/动名词 | `EmployeeService`、`ContractApprovalHandler` |
| 方法 | 小驼峰，动词开头 | `getById`、`submitApproval`、`cancelOrder` |
| 变量/参数 | 小驼峰 | `employeeId`、`contractAmount` |
| 常量 | 大写+下划线 | `MAX_LEAVE_DAYS`、`APPROVAL_PASS` |
| 数据库表 | `业务域_实体名` | `hr_employee`、`fin_voucher` |
| 字段 | 小写+下划线 | `employee_id`、`created_at` |
| 索引 | `idx_{table}_{cols}` | `idx_hr_employee_dept_id` |
| 唯一约束 | `uk_{table}_{cols}` | `uk_hr_employee_code` |
| API 路径 | `/{module}/{resource}/{action}` | `/hr/employee/submit` |
| MQ topic | `{module}.{event}.{version}` | `hr.employee.created.v1` |
| 缓存 key | `lumen:{module}:{bizType}:{bizId}` | `lumen:hr:employee:12345` |
| 事件命名 | 过去时态，名词 | `EmployeeCreatedEvent`、`ContractSignedEvent` |
| 状态枚举 | `{ENTITY}_{STATUS}` | `EMPLOYEE_ACTIVE`、`ORDER_PENDING` |

**禁用**：拼音、缩写歧义、Java 关键字、复数。

### 5.2 统一响应包装

```json
{ "code": 0, "message": "ok", "data": { ... }, "traceId": "abc123" }
```

所有 Controller 方法返回 `R<T>` / `R<PageResult<T>>`，由 `@RestControllerAdvice` + `ResponseBodyAdvice` 拦截。

### 5.3 错误码体系

```
HR_A001 = "员工 {0} 不存在"          // 10xxx = HR, A=业务, 001
FIN_B002 = "凭证借贷不平"             // 11xxx = 财务, B=校验
AUTH_C001 = "登录已过期，请重新登录"    // 00xxx = 通用, C=认证
SYS_D001 = "系统繁忙，请稍后重试"     // 99xxx = 系统, D=兜底
```

5 位数字：模块(2位) + 类型(1位) + 序号(2位)。客户端基于 `code` 做国际化/重试/告警分级。

### 5.4 异常体系

```
BizException（业务异常，可预期）
  ├─ NotFoundException           → 404
  ├─ AuthException              → 401
  ├─ PermissionException        → 403
  ├─ StateException             → 409
  ├─ ValidationException        → 400
  └─ ThirdPartyException        → 502
SysException（系统异常）
  └─ RuntimeException → 500
```

**核心规则**：直接 `throw new NotFoundException("HR_A001", employeeId)`，**绝不**捕获后吞掉再 `return R.fail()`。

### 5.5 统一日志

```java
@Log(title = "员工管理", businessType = BusinessType.UPDATE)
public R<Void> updateEmployee(@PathVariable Long id, @RequestBody EmployeeDTO dto) { ... }
```

JSON 格式：

```json
{ "ts": "2026-09-18T10:23:45.123+08:00", "level": "INFO", "traceId": "abc123",
  "userId": 1001, "tenantId": 1, "module": "hr", "action": "EmployeeService.update",
  "bizKey": "employee-12345", "costMs": 87, "msg": "更新员工成功" }
```

TraceID：网关生成 → MDC 注入 → Feign/MQ 透传 → ELK 检索。

### 5.6 数据权限（行级）

| 数值 | 含义 | SQL 注入片段 |
|---|---|---|
| 1 | 全部 | 无 |
| 2 | 本部门 | `AND dept_id = #{currentDeptId}` |
| 3 | 本部门及下级 | `AND dept_id IN (递归子部门)` |
| 4 | 本人 | `AND user_id = #{currentUserId}` |
| 5 | 自定义 | `AND dept_id IN #{customDeptIds}` |

实现：`@DataScope(deptAlias = "d", userAlias = "u")` + MyBatis 拦截器自动拼接。

### 5.7 分页

```java
PageQuery query = new PageQuery();
query.setPageNum(1); query.setPageSize(20);
query.setOrderByColumn("createTime"); query.setIsAsc("desc");
query.setParams(Map.of("deptId", 1001));
PageResult<EmployeeVO> result = employeeService.list(query);
```

底层：物理分页（MyBatis-Plus `Page` 或 LIMIT）。超过 100 万行的大表强制走 ES 或数仓。

### 5.8 幂等

| 场景 | 方案 | 实现 |
|---|---|---|
| 表单提交 | Token 机制 | 进入页面生成 token，提交后端删 token |
| API 调用 | 唯一业务号 | 客户端生成 `biz_no`，DB 唯一索引 |
| MQ 消费 | 业务唯一键 | `msg_id + consumer` 唯一索引 |

**禁用**：仅靠前端 disabled 防重。

### 5.9 缓存

**三级**：本地 Caffeine（1min TTL）→ Redis 集群（30min TTL）→ DB

```
业务 key:  lumen:{module}:{bizType}:{bizId}     e.g. lumen:hr:employee:12345
列表 key:  lumen:{module}:{bizType}:list:{hash}
锁 key:    lumen:lock:{module}:{bizType}:{bizId}
```

策略：读多写少用 Cache-Aside；写多读少用 Write-Through；强一致用分布式锁 + 延迟双删。

### 5.10 消息（MQ）约定

**Topic**：`{module}.{event}.v{version}`，如 `hr.employee.created.v1`

**消息格式**：

```json
{ "msgId": "snowflake-id", "eventType": "hr.employee.created.v1",
  "occurredAt": "2026-09-18T10:23:45Z", "tenantId": 1, "traceId": "abc123",
  "payload": { "employeeId": 12345, "name": "张三" } }
```

可靠性：本地消息表 + RocketMQ 事务消息；消费手动 ACK + 3 次重试 + 死信队列；幂等靠 `msgId` 去重。

### 5.11 领域事件

```java
// 发布方
applicationEventPublisher.publishEvent(
    new EmployeeCreatedEvent(this, employeeId, deptId));

// 订阅方
@RocketMQMessageListener(topic = "hr.employee.created.v1")
public class EmployeeCreatedListener implements RocketMQListener<EmployeeCreatedEvent> { ... }
```

**禁止**：跨服务直接 @Autowired 调用；跨服务 join 表。

### 5.12 审计日志

| 类型 | 表 | 触发点 | 保留期 |
|---|---|---|---|
| 操作审计 | `sys_oper_log`（RuoYi 自带） | `@Log` 注解 | 6 个月 |
| 数据审计 | `biz_audit_log`（每业务模块一张） | 状态机变更前后快照 | 业务生命周期 + 5 年 |
| 登录审计 | `sys_logininfor` | 登录成功/失败 | 6 个月 |
| 敏感审计 | `sys_audit_sensitive` | 审批/导出/删除/权限变更 | ≥ 5 年 |

`append-only` 表 + 定期归档 OSS + 月度哈希校验。

### 5.13 自动字段填充

每张业务表都包含（由 `MetaObjectHandler` 自动填充）：

```
tenant_id      BIGINT       NOT NULL
create_by      BIGINT       NOT NULL
create_time    DATETIME     NOT NULL
update_by      BIGINT       NOT NULL
update_time    DATETIME     NOT NULL
deleted        TINYINT      NOT NULL DEFAULT 0
```

**禁用**：物理删除（必须逻辑删除）；业务代码手动 set 审计字段。

### 5.14 时间、时区、ID

| 项 | 规则 |
|---|---|
| DB 时间字段 | `DATETIME`，不用 `TIMESTAMP` |
| 应用时区 | `Asia/Shanghai`，DB 连接强制 `serverTimezone=Asia/Shanghai` |
| API 返回时间 | ISO8601 `2026-09-18T10:23:45+08:00` |
| 分布式 ID | Snowflake（`yitter_idworker`） |
| 金额字段 | `DECIMAL(18,4)`，单位"元"，**绝不**用 `DOUBLE/FLOAT` |
| JSON 字段 | MySQL `JSON` / PG `JSONB` |
| 状态字段 | `TINYINT` + 字典（不要 hardcode 在注释里） |

### 5.15 模块必选依赖清单

```xml
<dependencies>
  <dependency>lumen-common-core       <!-- 响应/异常/上下文/工具 -->
  <dependency>lumen-common-mybatis    <!-- 分页/数据权限/字段填充 -->
  <dependency>lumen-common-security   <!-- 鉴权注解/上下文 -->
  <dependency>lumen-common-redis      <!-- 缓存封装 -->
  <dependency>lumen-common-mq         <!-- MQ 封装 -->
  <dependency>lumen-common-event      <!-- 事件发布/订阅 -->
  <dependency>lumen-common-web        <!-- Controller 增强（日志/限流/签名） -->
  <dependency>lumen-common-audit      <!-- 操作审计 -->
</dependencies>
```

---

## 6. 关键技术选型

### 6.1 微服务基础设施

| 组件 | 选型 | 决策理由 |
|---|---|---|
| 服务注册/配置 | **Nacos 2.x** | Spring Cloud Alibaba 全家桶标配；命名空间天然支持多租户 |
| 网关 | **Spring Cloud Gateway 4.x** | 与 Spring 生态最贴合；响应式高性能 |
| 服务调用 | **OpenFeign** | 声明式；与 Ribbon/Sentinel 无缝集成 |
| 负载均衡 | Ribbon（客户端） + Nginx（网关侧） | 双层 LB |

### 6.2 安全 / 鉴权

| 组件 | 选型 | 备注 |
|---|---|---|
| 认证框架 | **Spring Security 6.x + JJWT** | OAuth2/OIDC 完整；等保三级双因子易对接 |
| Token | **Access (2h) + Refresh (14d)** | App/H5 无状态；Refresh 存 Redis 支持撤销 |
| 密码 | **BCrypt (cost=12)** | 自带 Salt；如需国密引 bouncycastle |
| 数据权限 | **自研（MyBatis 拦截器）** | 5 级 data_scope 与业务耦合紧 |
| 接口签名 | **HMAC-SHA256（防重放）** | 财务/薪资等高敏感接口启用 |

### 6.3 稳定性 / 流量治理

| 组件 | 选型 |
|---|---|
| 限流/熔断 | **Sentinel 1.8+**（QPS/线程数/冷启动规则） |
| 分布式锁 | **Redisson** |
| 分布式 ID | **yitter Snowflake** |
| 分布式事务 | **Seata AT 模式**（金融强一致用 TCC） |

### 6.4 数据 / 缓存 / 搜索

| 组件 | 选型 |
|---|---|
| 关系数据库 | **MySQL 8.0** |
| 连接池 | **HikariCP** |
| 本地缓存 | **Caffeine** |
| 分布式缓存 | **Redis 7.x Cluster** |
| 搜索引擎 | **Elasticsearch 8.x** |
| 对象存储 | **MinIO**（私有） / **阿里云 OSS**（SaaS） |
| 分析数据库 | **Doris** |

### 6.5 消息 / 异步

| 组件 | 选型 |
|---|---|
| 消息队列 | **RocketMQ 5.x** |
| 延迟消息 | **RocketMQ 内置**（18 个固定级别） |
| 本地消息表 | **自研 + RocketMQ 事务消息** |

### 6.6 流程 / 业务引擎

| 组件 | 选型 |
|---|---|
| 审批流 | **自研状态机 + Flowable 7.x 双方案**（统一抽象 `ApprovalEngine`） |
| 规则引擎 | **Aviator / Easy Rules** |
| 代码生成器 | **RuoYi 自带 gen + 自研 DDD 模板** |
| 表单设计器 | **自研**（基于 FormMaking/FormDesigner） |

### 6.7 集成 / 第三方

| 组件 | 选型 |
|---|---|
| 协作平台 | **抽象 `CollaborationAdapter`，默认钉钉**（接口预留企微/飞书） |
| 电子签章 | **抽象 adapter，默认契约锁**（可切法大大/e签宝） |
| 银行 | **银企直联 SDK** |
| 金税 | **航天信息 OpenAPI** |
| OCR | **百度 OCR + 腾讯云 OCR（双备）** |
| 短信 | **阿里云 + 腾讯云（双通道）** |
| 邮件 | **JavaMail + 异步发送** |
| 地图 | **高德地图** |

### 6.8 可观测性

| 组件 | 选型 |
|---|---|
| 指标监控 | **Prometheus + Grafana** |
| 链路追踪 | **SkyWalking 9.x**（自动探针） |
| 日志 | **ELK（Filebeat → Logstash → ES → Kibana）** |
| 告警 | **AlertManager + 钉钉/企微 Webhook** |
| 在线诊断 | **Arthas** |

### 6.9 DevOps

| 组件 | 选型 |
|---|---|
| 代码托管 | **Gitea**（私有）/ GitLab |
| CI/CD | **Jenkins + JCasC**（Pipeline as Code） |
| 镜像仓库 | **Harbor** |
| 容器化 | **Docker + Docker Compose（开发）/ Kubernetes（生产）** |
| IaC | **Helm Chart + Terraform（可选）** |
| 代码质量 | **SonarQube + Checkstyle + Spotless** |

### 6.10 前端

| 组件 | 选型 |
|---|---|
| PC Web | **Vue 3 + TypeScript + Vite + Element Plus** |
| 状态管理 | **Pinia** |
| HTTP | **Axios + 拦截器** |
| H5 | **Vue 3 + Vant 4** |
| 原生 App | **Flutter**（推荐） / **uni-app** |
| 图表 | **ECharts 5** |
| 工作流设计器 | **LogicFlow / AntV X6** |

---

## 7. 多租户 + 合规 + 安全

### 7.1 多租户设计（Hybrid）

#### 7.1.1 部署模式开关

```yaml
lumen:
  deployment:
    mode: saas    # private | saas
  tenant:
    enabled: true
    isolation: row   # row | schema
```

| 维度 | private | saas |
|---|---|---|
| 网关是否解析 tenant | 注入固定值 `0` | 从 JWT/Header/子域名解析 |
| MyBatis 拦截器是否过滤 | 不过滤 | 自动 `WHERE tenant_id = ?` |
| 超级管理后台 | 不启用 | 启用 |

#### 7.1.2 租户解析优先级

```
1. JWT 中的 tenant_id（登录后固定）         优先级最高
2. HTTP Header: X-Tenant-Id              用于内部切换
3. 子域名：{tenantCode}.lumen.com         用户访问入口
4. 默认租户（私有模式）                   兜底
```

#### 7.1.3 租户上下文传递

```
HTTP 请求 → Gateway(解析 tenant_id) → MDC + Request Context
                                       ↓
                          Feign 调用（Header 透传）
                                       ↓
                          MQ 消息（payload 携带 tenant_id）
                                       ↓
                          异步任务（XXL-JOB 参数携带）
                                       ↓
                          落库（每条记录带 tenant_id）
```

#### 7.1.4 租户隔离边界（绝对不能漏）

- 缓存 key 必须包含 `tenant_id`
- MQ topic 消息必须携带 `tenant_id`
- 文件 OSS 路径必须包含 `tenant_id/{biz}/...`

#### 7.1.5 租户生命周期

```
注册（试用 30 天） → 认证 → 付费（选套餐） → 续费 / 过期 / 冻结 / 注销
                                                          ↓
                                                  数据保留 90 天 → 物理删除
```

### 7.2 等保三级要求

#### 7.2.1 安全通信

| 要求 | 实现 |
|---|---|
| 全站 HTTPS | Nginx/ALB 终结 TLS 1.2+；强制 HTTP→HTTPS 301 |
| 内部调用加密 | 微服务间 mTLS 或国密 SM2 通道 |
| WebSocket | WSS 协议 |
| 第三方回调签名 | HMAC-SHA256 + 时间戳（5min）+ 随机串防重放 |

#### 7.2.2 安全认证

| 要求 | 实现 |
|---|---|
| 密码策略 | 8 位 + 大小写+数字+特殊；90 天强制修改；历史 5 次不能重复 |
| 登录失败锁定 | 5 次失败锁定 30min（Redis 计数） |
| 双因素认证 | 管理员/财务/HR 强制开启（TOTP 或短信） |
| 会话超时 | PC 30min / App 7d |
| 单点登录 | 钉钉/企微/飞书扫码 + CAS |
| 密码加密 | BCrypt；绝不存明文；绝不日志记录 |

#### 7.2.3 访问控制

| 要求 | 实现 |
|---|---|
| RBAC | 用户-角色-权限（菜单/按钮/接口/API） |
| 数据权限 | 5 级 data_scope |
| 最小权限 | 默认仅授予必要角色，权限申请走审批 |
| 特权账号 | 超级管理员开启 MFA + 操作实时告警 + IP 白名单 |

#### 7.2.4 安全审计

详见 §5.12。审计不可篡改：`append-only` 表 + 定期归档 OSS + 月度哈希校验。

#### 7.2.5 数据保护

| 要求 | 实现 |
|---|---|
| 敏感字段加密 | 手机/身份证/银行卡/邮箱（SM4 或 AES-256-GCM） |
| 数据库透明加密 | RDS TDE / ShardingSphere 数据加密 |
| 备份与恢复 | 每日全量 + 每 15min 增量；RPO ≤ 15min，RTO ≤ 4h |
| 异地容灾 | 同城双活 + 异地灾备（OSS 冷归档） |
| 数据脱敏 | 非授权查询自动 `138****8000` |

### 7.3 敏感数据处理

#### 7.3.1 字段级加密

```java
@SensitiveField(type = SensitiveType.ID_CARD)
private String idCard;

@SensitiveField(type = SensitiveType.MOBILE)
private String mobile;
```

**密钥管理**：私有部署本地 KMS（Vault 或自研）；SaaS 用阿里云/腾讯云 KMS。
**轮转策略**：主密钥每年轮转；数据密钥按业务模块分离（人事/财务/客户）。

#### 7.3.2 脱敏规则

| 字段 | 脱敏示例 | 规则 |
|---|---|---|
| 手机号 | `138****8000` | 前 3 + **** + 后 4 |
| 身份证 | `110101********1234` | 6 位 + ****** + 4 位 |
| 银行卡 | `6222 **** **** 1234` | 4 + 三组 **** + 4 |
| 邮箱 | `z***@example.com` | 首字母 + *** + @域名 |
| 薪资 | `****` | 完全隐藏（需薪资模块授权） |
| 姓名 | `张*` | 仅本人和上级可见全名 |

### 7.4 数据权限 vs 多租户

| 维度 | 多租户隔离 | 数据权限 |
|---|---|---|
| 目的 | 防止公司 A 看到公司 B 的数据 | 防止员工看到不该看的部门数据 |
| 维度 | 租户级（公司间） | 部门级（公司内） |
| 字段 | `tenant_id` | `dept_id` + `data_scope` |
| 谁不能绕过 | 任何人（含超管） | 仅本人 + 超管可绕过 |

### 7.5 合规审计检查清单

**必做项**：HTTPS、密码策略、登录锁定、双因素、操作日志、二次确认、字段加密、列表脱敏、导出审批 + 水印、备份演练、漏洞扫描、渗透测试。

**推荐项**：WAF、DDoS、HIDS、代码审计、组件漏洞扫描、Dev/Test 数据脱敏。

---

## 8. CI/CD + 监控 + 灰度

### 8.1 环境分层

| 环境 | 数据 | 域名 | 触发 |
|---|---|---|---|
| dev | 假数据 | dev.lumen.local | 提交即部署 |
| test | 假数据 | test.lumen.com | PR 创建/更新 |
| staging | 生产数据脱敏快照 | staging.lumen.com | release 分支 |
| prod | 真实数据 | lumen.com | tag + 人工审批 |

### 8.2 CI/CD Pipeline

**质量门禁**：

| 检查 | 工具 | 阈值 |
|---|---|---|
| 单元测试覆盖率 | Jacoco | ≥ 70%（Service ≥ 85%） |
| 代码规范 | Checkstyle + Spotless | 0 error |
| 漏洞扫描 | OWASP Dependency-Check | High/Critical = 0 |
| 镜像扫描 | Trivy | High/Critical = 0 |
| API 兼容性 | OpenAPI Diff | Breaking 阻断 |

**分支策略**：

```
main              受保护，只能通过 PR 合并
  ├── feature/*   功能分支
  ├── release/*   发布分支（测试通过后打 tag）
  └── hotfix/*    紧急修复
```

### 8.3 部署策略

| 模式 | 适用 | 实现 |
|---|---|---|
| 滚动发布 | 普通业务 | K8s 默认 |
| 蓝绿发布 | 关键服务（财务/HR） | 两套集群瞬时切换，旧版保留 1h |
| 灰度发布 | 大版本/不确定改动 | 按租户/标签/流量百分比渐进放量 |

**灰度回滚条件**：错误率 > 1% 持续 5min / P99 > 基线 2 倍 / 关键业务指标异常。

**数据库变更**：严格 expand → migrate → contract 三步法；Flyway/Liquibase 版本化。

### 8.4 监控

**三大支柱**：Prometheus（指标）/ ELK（日志）/ SkyWalking（链路）。

**关键指标**：

| 类别 | 指标 | 黄色告警阈值 |
|---|---|---|
| 可用性 | service_up | < 1 持续 1min |
| QPS | http_requests_total | 突降 50% |
| 错误率 | 5xx / total | > 1% 持续 5min |
| 延迟 | P99 | > 2s |
| JVM | heap_used / heap_max | > 80% |
| DB 连接池 | active / max | > 80% |
| Redis 内存 | used / max | > 70% |

**日志规范**：所有服务统一 JSON，字段包含 `ts/level/service/instance/traceId/spanId/userId/tenantId/module/action/bizKey/costMs/msg`。
**采样率**：生产 10%；错误请求 100%。

### 8.5 告警分级

| 级别 | 条件 | 通知 | 响应 |
|---|---|---|---|
| P0 | 全站不可用 / 数据丢失 / 安全事件 | 电话 + 钉钉 + 短信 | 5min |
| P1 | 核心业务异常 | 钉钉 + 企微 | 15min |
| P2 | 单服务异常 | 钉钉群 | 1h |
| P3 | 资源预警 | 邮件 | 4h |

收敛：5min 同源合并；P1 30min 未确认升级 P0。

### 8.6 备份与灾备

| 数据 | 频率 | 保留期 | 演练 |
|---|---|---|---|
| 数据库全量 | 每日 02:00 | 30d 本地 + 1y OSS 冷归档 | 季度 |
| 数据库增量 | 每 15min binlog | 7d | 季度 |
| 文件存储 | 实时双副本 + 跨可用区 | 永久 | 半年度 |
| 配置中心 | 变更即同步到 Git | 永久 | 变更前 |
| 日志 | 热 7d / 冷 90d / OSS 1y | 1y | 不演练 |

**RPO ≤ 15min，RTO ≤ 4h**。

---

## 9. 演进路线图（P0 → P4）

### 9.1 总览时间轴

```
        月份:  1   2   3   4   5   6   7   8   9  10  11  12
                ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
P0 基础设施     ████████████████████████                           
P1 基础业务             ████████                                          
P2 核心业务                       ████████████████                               
P3 业务协同                                          ████████████████████                          
P4 高级能力                                                              ████████████                                
                └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
里程碑:           M1   M1  M2       M3            M4           M5           M6
```

**总工期估算**：
- 1-2 人小团队：12-15 个月
- 3-5 人团队：8-10 个月（关键路径并行）
- 6+ 人团队：6-8 个月（业务并行）

### 9.2 详细计划

| Phase | 子项目 | 工期 | 依赖 | 关键产出 |
|---|---|---|---|---|
| **P0** | platform-base | 5w | RuoYi-Cloud | 多租户/异常/幂等/响应/代码生成器/CI/CD |
| P0 | auth-rbac | 3w | platform-base | 用户/角色/部门/数据权限/SSO |
| P0 | gateway | 2w | platform-base | 路由/限流/灰度/熔断 |
| P0 | workflow-engine | 6w | platform-base | 流程设计器 + 审批引擎 + 任务中心 |
| P0 | file-storage | 2w | platform-base | MinIO 抽象 + 上传/预览 |
| P0 | message-center | 3w | platform-base | 站内信/邮件/短信 + 钉钉推送 |
| P0 | system-mgmt | 1w | platform-base | 字典/配置/定时任务/审计 |
| **P1** | org-structure | 3w | P0 全套 | 部门树/岗位/职级/汇报线/编制 |
| P1 | attendance-leave | 3w | org, workflow | 排班/打卡/请假/出差/节假日 |
| **P2** | hr | 8w | org, workflow | 员工档案/招聘/调岗/绩效/培训/离职 |
| P2 | finance | 10w | workflow, message | 科目/凭证/总账/应收应付/报销/预算/发票 |
| P2 | assets | 5w | org, workflow | 资产卡片/领用/调拨/盘点/折旧 |
| **P3** | procurement | 6w | finance, assets | 供应商/询比价/采购单/收货/付款 |
| P3 | contract | 6w | workflow, file, procurement | 起草/审批/电子签/履约/到期 |
| P3 | inventory | 5w | procurement, sales | 入库/出库/调拨/盘点 |
| P3 | sales-crm | 6w | finance, contract | 客户/商机/报价/订单/回款 |
| **P4** | payroll | 6w | hr, finance | 算薪/个税/社保/银行报盘 |
| P4 | bi-reporting | 8w | 全部业务库 | 数仓/指标体系/可视化大屏 |
| P4 | mobile-integration | 8w | auth, message, workflow | App/H5 + 钉钉/企微/飞书 |

### 9.3 里程碑

- **M1**（第 6 周末）：平台 + 认证可演示；CI/CD 跑通
- **M2**（第 12 周末）：工作流 + 文件 + 消息 + 多租户切换演示
- **M3**（第 16 周末）：组织 + 考勤全闭环
- **M4**（第 28 周末）：HR/财务/资产 + 跨模块事件跑通
- **M5**（第 36 周末）：17 个模块全部交付，完整业务闭环
- **M6**（第 48 周末）：薪资 + BI + 移动端 + 等保三级测评通过

### 9.4 资源安排（推荐配置）

```
关键路径：platform-base → workflow → hr → finance → payroll

并行轨道：
A: platform + auth + workflow + hr + finance + payroll
B: gateway + system + org + assets
C: file + message + procurement + inventory + sales
D: contract + bi + mobile

资源需求（3-5 人团队）：
- 后端：3-5 人（Java）
- 前端：2 人（Vue 1 人 + App 1 人）
- 测试：1-2 人（功能 + 自动化）
- 运维：1 人（兼职）
- 产品：1 人（跨业务）
```

### 9.5 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| RuoYi 升级兼容性 | 高 | 锁定版本，自己维护 patch fork |
| Flowable 学习曲线 | 中 | 优先用自研状态机（覆盖 80% 场景），Flowable 仅复杂 BPMN 用 |
| 多租户数据 bug 难排查 | 高 | 强制所有 SQL 走拦截器；CI 加租户隔离测试 |
| 财务合规风险 | 高 | 引入外部财务顾问；金税对接选成熟厂商 |
| 第三方集成不稳定 | 中 | Adapter 抽象 + 多备选；关键流程异步 + 重试 |
| 团队对 RuoYi 不熟 | 中 | 内部培训 + 沉淀最佳实践 wiki |
| BI 数仓性能 | 中 | MVP 先用 Doris，后期再考虑 ClickHouse |

### 9.6 每个子项目交付物

```
/子项目名/
├── docs/
│   ├── design.md          业务级设计 spec
│   └── api/               OpenAPI 3.0
├── src/main/java/
├── src/main/resources/
│   ├── db/migration/      Flyway 脚本（V1__init.sql, V2__...）
│   └── mapper/            MyBatis XML
├── src/test/java/         单元测试（≥70% 覆盖）
└── README.md
```

### 9.7 上线策略

```
内部 UAT（1 个部门 2 周）→ 灰度发布（按部门 4 周）→ 全量上线（双轨 2 周）→ 旧系统下线
```

---

## 10. 风险与决策记录（ADR）

每个 ADR 落到 `docs/superpowers/adr/{NNNN}-{title}.md`，模板：

```markdown
# ADR-NNNN: 决策标题
## 状态 2026-09-18 已决策
## 背景
## 决策
## 备选方案
## 后果（优点/缺点/风险）
```

### 已落地的 15 份 ADR

| 编号 | 标题 | 状态 |
|---|---|---|
| [ADR-0001](../adr/0001-use-nacos-for-registry-and-config.md) | 服务注册与配置中心选型 Nacos | ✅ |
| [ADR-0002](../adr/0002-single-db-with-business-prefix.md) | 数据库单库起步 + 业务前缀命名 | ✅ |
| [ADR-0003](../adr/0003-multi-tenant-row-level-isolation.md) | 多租户行级隔离（暂不上库级） | ✅ |
| [ADR-0004](../adr/0004-workflow-dual-engine.md) | 审批流自研状态机 + Flowable 双方案 | ✅ |
| [ADR-0005](../adr/0005-mobile-app-flutter.md) | 移动端 Flutter 跨端方案（暂定） | ✅ |
| [ADR-0006](../adr/0006-seata-at-mode-for-distributed-tx.md) | 分布式事务 Seata AT 模式 | ✅ |
| [ADR-0007](../adr/0007-rocketmq-as-message-broker.md) | 消息队列 RocketMQ 5.x | ✅ |
| [ADR-0008](../adr/0008-elasticsearch-for-search.md) | 搜索引擎 Elasticsearch 引入必要性 | ✅ |
| [ADR-0009](../adr/0009-flyway-for-db-migration.md) | 数据库迁移 Flyway | ✅ |
| [ADR-0010](../adr/0010-api-response-and-error-code.md) | API 响应包装与错误码体系 | ✅ |
| [ADR-0011](../adr/0011-data-permission-5-levels.md) | 数据权限 5 级模型 | ✅ |
| [ADR-0012](../adr/0012-three-tier-cache.md) | 缓存三级架构 + Redis Cluster | ✅ |
| [ADR-0013](../adr/0013-integration-adapter-pattern.md) | 集成层 Adapter 模式 | ✅ |
| [ADR-0014](../adr/0014-canary-release-strategy.md) | 灰度发布策略 | ✅ |
| [ADR-0015](../adr/0015-skywalking-for-tracing.md) | 链路追踪 SkyWalking | ✅ |

### 后续待补充 ADR（运行时遇到时新增）

- BI 数仓 Doris 选型详细对比
- 财务模块合规架构（等保三级专项）
- 移动端离线缓存策略
- 大文件分片上传协议
- 电子签章法律效力（中国电子签名法 + 证据链）

---

## 11. 后续路径

### 已完成（本 spec 范围）

1. ✅ 用户审阅本 spec，提出修改意见（如有）
2. ✅ 拆分系统范围，识别 17 个子项目
3. ✅ 顶层架构图（用户层 → 接入层 → 网关层 → 微服务层 → 中间件 → 数据层 → 集成层）
4. ✅ 共用规范（14 项横切关注点）
5. ✅ 关键技术选型（10 大类）
6. ✅ 多租户 + 合规 + 安全
7. ✅ CI/CD + 监控 + 灰度
8. ✅ 演进路线图（P0→P4）
9. ✅ 附录 A：17 个子项目详细概览
10. ✅ 附录 B：8 个跨模块事件流时序图
11. ✅ 附录 C：NFR + SLA 矩阵
12. ✅ 附录 D：术语表
13. ✅ 附录 E：数据流全景示例
14. ✅ 附录 F：RuoYi 覆盖映射表（19+2 张表覆盖关系）
15. ✅ 附录 G：DB Schema 设计示例（3 个基础模块完整 Flyway 脚本）
16. ✅ 附录 H：安全详细设计（等保三级五大要求 + 安全测试用例 + 渗透测试清单）
14. ✅ 15 份 ADR 决策记录

### 下一阶段（用户在决定后启动）

1. **路线 A**：从 P0 开始正式做——挑一个子项目（推荐 `01-platform-base`），调用 `writing-plans` 技能出它的**实施计划**，然后开始写代码
2. **路线 B**：继续完善顶层 spec（例如：每个子项目的 ADR 子集、详细的 DB Schema 示例、关键流程的 C4 模型图）

---

## 12. 文档结构索引

```
docs/superpowers/
├── specs/
│   └── 2026-09-18-lumen-ai-company-manage-design.md    ← 本文档（顶层）
│
├── appendices/                                          ← 顶层附录
│   ├── A-17-subprojects-overview.md                     ← 17 个子项目概览
│   ├── B-event-flow-diagrams.md                        ← 跨模块事件流时序图
│   ├── C-nfr-sla-matrix.md                             ← NFR + SLA 矩阵
│   ├── D-glossary.md                                   ← 术语表
│   └── E-data-flow-example.md                          ← 数据流全景示例
│
├── adr/                                                ← 架构决策记录
│   ├── 0001-use-nacos-for-registry-and-config.md
│   ├── 0002-single-db-with-business-prefix.md
│   ├── 0003-multi-tenant-row-level-isolation.md
│   ├── 0004-workflow-dual-engine.md
│   ├── 0005-mobile-app-flutter.md
│   ├── 0006-seata-at-mode-for-distributed-tx.md
│   ├── 0007-rocketmq-as-message-broker.md
│   ├── 0008-elasticsearch-for-search.md
│   ├── 0009-flyway-for-db-migration.md
│   ├── 0010-api-response-and-error-code.md
│   ├── 0011-data-permission-5-levels.md
│   ├── 0012-three-tier-cache.md
│   ├── 0013-integration-adapter-pattern.md
│   ├── 0014-canary-release-strategy.md
│   └── 0015-skywalking-for-tracing.md
│
└── 17 个子项目设计 spec（后续每个项目单独创建）
    ├── platform-base/design.md
    ├── auth-rbac/design.md
    ├── ...
    └── mobile-integration/design.md
```

---

## 附录 A：服务端口与库名清单

| 服务 | 端口 | 库 |
|---|---|---|
| platform-gateway | 8080 | - |
| auth-service | 9200 | lumen_db |
| system-service | 9201 | lumen_db |
| workflow-service | 9202 | lumen_db |
| file-service | 9203 | lumen_db |
| message-service | 9204 | lumen_db |
| org-service | 9210 | lumen_db |
| hr-service | 9211 | lumen_db |
| finance-service | 9212 | lumen_db |
| assets-service | 9213 | lumen_db |
| procurement-service | 9214 | lumen_db |
| contract-service | 9215 | lumen_db |
| inventory-service | 9216 | lumen_db |
| sales-service | 9217 | lumen_db |
| payroll-service | 9218 | lumen_db |
| bi-service | 9219 | lumen_bi_db |
| mobile-gateway | 9300 | - |

---

## 附录 B：变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1.0 | 2026-09-18 | 初稿（顶层架构 + 共用规范 + 技术选型 + 合规 + CI/CD + 路线图） | Claude（brainstorming 协作产出） |
| v1.1 | 2026-09-18 | 补充 5 份附录 + 15 份 ADR；增加文档索引；标注子文档路径 | Claude |
