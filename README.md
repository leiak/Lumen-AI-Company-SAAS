# Lumen AI Company Manage

> 一套对标用友 / 金蝶 / SAP 的国产化企业经营管理系统，基于 RuoYi-Cloud 微服务架构。
> 覆盖 **人力、财务、合同、采购、资产、库存、销售、CRM、薪资、BI、OA** 等核心业务，同代码同时支持**私有部署**与 **SaaS 多租户**两种交付模式。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.2-6DB33F?logo=spring&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vue.js&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## 📑 目录

- [项目背景与目标](#-项目背景与目标)
- [核心特性](#-核心特性)
- [技术栈](#-技术栈)
- [仓库结构](#-仓库结构)
- [17 个核心子项目](#-17-个核心子项目)
- [部署模式](#-部署模式)
- [快速开始](#-快速开始)
- [架构与文档](#-架构与文档)
- [ADR 关键决策一览](#-adr-关键决策一览)
- [合规与安全](#-合规与安全)
- [Roadmap](#-roadmap)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)

---

## 🎯 项目背景与目标

### 背景

随着企业数字化深入，OA、人力、财务、采购、销售等系统割裂严重，数据难打通、流程难协同。
我们需要构建一套**完整、统一、可扩展**的企业经营管理系统，既能**私有部署**满足大型客户的安全合规要求，
也能 **SaaS 化运营** 面向中小客户提供开箱即用服务。

### 目标

- ✅ 17 个核心业务模块全自研覆盖（人力 / 财务 / 合同 / 采购 / 资产 / 库存 / 销售 / CRM / 薪资 / BI 等）
- ✅ 同代码同时支持**私有部署**与 **SaaS 多租户** 两种交付模式
- ✅ 满足**等保三级**合规要求（审计、加密、双因素、敏感数据保护）
- ✅ 多端接入：PC Web + H5 + 原生 App + 钉钉 / 企微 / 飞书
- ✅ 可对接电子签章、金税、银行、SMS / 邮件 / OCR / 地图等关键第三方

### 非目标（明确不做）

- ❌ 生产制造（MES）模块
- ❌ 电商前台（C 端商城）
- ❌ 财务总账的行业专版（保险 / 银行）
- ❌ 区块链存证与 AI 大模型深度集成（仅预留接口）

---

## ✨ 核心特性

| 维度 | 能力 |
|---|---|
| **业务覆盖** | 人力、财务、合同、采购、资产、库存、销售、CRM、薪资、BI、审批、消息、报表 17 个核心模块 |
| **交付模式** | 私有部署 + SaaS 多租户（行级隔离起步，可升级到库级） |
| **多端接入** | PC Web、H5、原生 App、钉钉 / 企微 / 飞书生态 |
| **合规** | 等保三级（审计日志、字段级加密、双因素认证、敏感数据脱敏） |
| **数据** | MySQL 8.0 单库（`lumen_db`），BI 数仓独立（`lumen_bi_db`） |
| **事务** | Seata AT 模式保障分布式事务一致性 |
| **搜索** | Elasticsearch 8.x 全文检索与聚合查询 |
| **缓存** | 三级缓存（Caffeine + Redis + 本地二级），热点数据毫秒级返回 |
| **追踪** | SkyWalking 全链路追踪 + 慢 SQL / 慢接口定位 |
| **工作流** | 自研双引擎（BPMN + 自定义 DSL），复杂审批可配置 |

---

## 🛠️ 技术栈

### 后端

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | LTS |
| Spring Boot | 3.5.5 | 微服务底座 |
| Spring Cloud | 2025.0.2 | 微服务框架 |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos / Sentinel / Seata |
| MyBatis | 3.0.5 | ORM |
| Nacos | 2.x | 注册中心 + 配置中心 |
| Sentinel | 1.8.x | 限流熔断 |
| Seata | 1.8+ | 分布式事务（AT 模式） |
| Redis | 7.x | 缓存 + 会话 |
| MySQL | 8.0 | 主业务库 |
| Elasticsearch | 8.x | 搜索与分析 |
| RocketMQ | 5.x | 消息队列 |
| MinIO | 8.x | 对象存储 |
| Flyway | 10.x | 数据库迁移 |
| Spring Boot Admin | 3.5.x | 服务监控 |

### 前端

| 组件 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5 | 渐进式框架 |
| TypeScript | 5.6 | 类型增强 |
| Vite | 6.4 | 构建工具 |
| Element Plus | 2.13 | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由 |
| ECharts | 5.6 | 图表 |
| VueUse | 14.x | 组合式工具集 |

### DevOps

- **CI/CD**：GitHub Actions / Jenkins（按部署环境选择）
- **容器化**：Docker + Docker Compose（开发与测试）
- **编排**：Kubernetes（生产）
- **服务网格**：Istio（可选）
- **镜像仓库**：Harbor
- **日志**：ELK（Elasticsearch + Logstash + Kibana）
- **监控**：Prometheus + Grafana + SkyWalking

---

## 📂 仓库结构

```
lumen-ai-company-manage/
├── RuoYi-Cloud-springboot3/         # 后端 — Spring Boot 3 + Spring Cloud 微服务
│   ├── ruoyi-api/                   # 内部 API 网关层（Feign 接口）
│   ├── ruoyi-auth/                  # 统一认证中心（OAuth2 + JWT）
│   ├── ruoyi-common/                # 公共模块（工具类、统一返回、异常、租户上下文）
│   ├── ruoyi-gateway/               # Spring Cloud Gateway 网关
│   ├── ruoyi-modules/               # 业务模块（人力、财务、合同、采购、CRM 等）
│   ├── ruoyi-visual/                # 可视化（监控、代码生成、Swagger 聚合）
│   ├── sql/                         # 初始化 SQL（quartz、ry-config、ry-seata 等）
│   ├── docker/                      # Docker 编排与镜像构建
│   └── pom.xml                      # 父 POM
│
├── RuoYi-Cloud-Vue3-typescript/     # 前端 — Vue 3 + TypeScript + Vite
│   ├── src/                         # 业务源码
│   │   ├── api/                     # 后端接口封装
│   │   ├── views/                   # 页面视图
│   │   ├── components/              # 通用组件
│   │   ├── router/                  # 路由
│   │   ├── store/                   # Pinia 状态
│   │   └── utils/                   # 工具方法
│   ├── public/                      # 静态资源
│   ├── vite/                        # Vite 配置
│   └── package.json
│
├── docs/                            # 架构与设计文档
│   └── superpowers/
│       ├── specs/                  # 顶层架构设计 Spec
│       ├── adr/                     # 15 份架构决策记录
│       ├── appendices/             # 8 份附录（流程图、ER、NFR、词汇表、安全等）
│       └── plans/                   # 实施计划
│
└── README.md                        # 本文件
```

---

## 🧩 17 个核心子项目

| # | 子项目 | 模块代号 | 说明 |
|---|---|---|---|
| 01 | 平台基础 | `platform-base` | 租户、字典、配置、审计、通知、文件存储、限流 |
| 02 | 认证授权 | `auth-rbac` | OAuth2 + JWT + RBAC + 数据权限（5 级）+ 双因素 |
| 03 | 组织架构 | `org-structure` | 公司 / 部门 / 岗位 / 人员 / 编制 / 汇报线 |
| 04 | 人力资源 | `hr-core` | 招聘、入转调离、考勤、绩效、培训、员工自助 |
| 05 | 薪资管理 | `payroll` | 薪资项、算薪、个税、社保、银行报盘 |
| 06 | 财务管理 | `finance-gl` | 总账、应收应付、费用报销、出纳、银行对账 |
| 07 | 合同管理 | `contract` | 合同模板、签订、变更、履约、归档、电子签章 |
| 08 | 采购管理 | `procurement` | 供应商、询比价、订单、收货、质检、应付 |
| 09 | 销售订单 | `sales-order` | 客户、报价、订单、发货、退货、应收账款 |
| 10 | 库存管理 | `inventory` | 仓库、商品、批次 / 序列号、出入库、盘点、调拨 |
| 11 | 资产设备 | `asset` | 资产卡片、折旧、调拨、盘点、报废 |
| 12 | CRM 客户 | `crm` | 线索、商机、客户、跟进、合同、收款 |
| 13 | 工作流引擎 | `workflow-engine` | 自研双引擎（BPMN + DSL），流程设计器 |
| 14 | OA 审批 | `oa-approval` | 用车、用印、报销、出差、加班、请假 |
| 15 | 消息中心 | `msg-center` | SMS、邮件、站内信、App Push、企微 / 钉钉 |
| 16 | 报表 BI | `bi-report` | 自助报表、看板、抽取数仓（`lumen_bi_db`） |
| 17 | 移动 App | `mobile-app` | Flutter / uni-app 跨端，对接钉钉 / 企微 / 飞书 |

> 各子项目独立迭代、独立部署，详细设计见 `docs/superpowers/specs/`。

---

## 🚀 部署模式

### 模式一：私有部署（Private）

```
客户机房 / VPC ──► 全栈一体部署
├─ 单库 lumen_db（MySQL 8.0）
├─ Redis / MinIO / RocketMQ / ES 同机或独立部署
└─ 无租户隔离（或按部署实例天然隔离）
```

适用：政府、央国企、金融、大型企业。

### 模式二：SaaS 多租户（SaaS Multi-Tenant）

```
云平台 ──► 统一服务集群
├─ 共享单库 lumen_db（行级隔离起步）
├─ 租户上下文（TenantContext）穿透各层
├─ 共享 Redis / MQ / ES
└─ 大客户可升级到独立库（schema 隔离）
```

适用：中小企业、初创团队、付费用户。

### 双模式实现要点

- 同一套代码、同一份镜像，通过配置 `deploy.mode=private|sass` 切换
- 租户上下文自动注入（Gateway → Feign → MyBatis Interceptor）
- 行级过滤 `tenant_id` 兜底 + 框架内幂等切面兜底

---

## ⚡ 快速开始

### 前置环境

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20.x LTS | 前端构建 |
| pnpm / npm | pnpm ≥ 9 / npm ≥ 10 | 前端包管理 |
| Docker | 24+ | 中间件容器化 |
| Docker Compose | v2 | 本地编排 |

### 1. 克隆仓库

```bash
git clone git@github.com:leiak/Lumen-AI-Company-SAAS.git
cd Lumen-AI-Company-SAAS
```

### 2. 启动基础设施（MySQL / Redis / Nacos / Seata / RocketMQ / MinIO / ES）

```bash
cd RuoYi-Cloud-springboot3/docker
docker compose up -d
```

> 默认端口与密码见 `docker/.env`（**生产环境务必修改**）。

### 3. 初始化数据库

```bash
# 创建主库 + 各微服务 schema
mysql -uroot -p < sql/ry-cloud.sql

# 启动后由 Flyway 自动执行版本化迁移（versioned + repeatable）
```

### 4. 启动后端

```bash
cd RuoYi-Cloud-springboot3
mvn clean install -DskipTests
# 依次启动
mvn -pl ruoyi-gateway spring-boot:run
mvn -pl ruoyi-auth spring-boot:run
mvn -pl ruoyi-modules/ruoyi-system spring-boot:run
# ... 按需启动其他模块
```

### 5. 启动前端

```bash
cd RuoYi-Cloud-Vue3-typescript
pnpm install   # 或 npm install
pnpm dev       # 开发模式，默认 http://localhost:80
```

### 6. 访问

| 入口 | 地址 |
|---|---|
| 前端 | http://localhost:80 |
| Gateway | http://localhost:8080 |
| Nacos | http://localhost:8848/nacos （nacos/nacos） |
| Swagger 聚合 | http://localhost:8080/swagger-ui.html |

> 默认账号：`admin / admin123`（**生产环境务必修改**）

---

## 📚 架构与文档

| 文档 | 路径 | 说明 |
|---|---|---|
| 顶层架构设计 | [`docs/superpowers/specs/2026-09-18-lumen-ai-company-manage-design.md`](docs/superpowers/specs/2026-09-18-lumen-ai-company-manage-design.md) | v1.1 已批准 |
| 子项目概览 | [`docs/superpowers/appendices/A-17-subprojects-overview.md`](docs/superpowers/appendices/A-17-subprojects-overview.md) | 17 个子项目详细说明 |
| 事件流程图 | [`docs/superpowers/appendices/B-event-flow-diagrams.md`](docs/superpowers/appendices/B-event-flow-diagrams.md) | 8 个核心场景时序图 |
| NFR + SLA | [`docs/superpowers/appendices/C-nfr-sla-matrix.md`](docs/superpowers/appendices/C-nfr-sla-matrix.md) | 非功能需求与容量规划 |
| 术语表 | [`docs/superpowers/appendices/D-glossary.md`](docs/superpowers/appendices/D-glossary.md) | 缩写与定义 |
| 数据流示例 | [`docs/superpowers/appendices/E-data-flow-example.md`](docs/superpowers/appendices/E-data-flow-example.md) | 一个 HTTP 请求全链路 |
| RuoYi 表覆盖 | [`docs/superpowers/appendices/F-ruoyi-coverage-mapping.md`](docs/superpowers/appendices/F-ruoyi-coverage-mapping.md) | 继承 / 扩展 / 替换 / 新增 |
| DB Schema 示例 | [`docs/superpowers/appendices/G-db-schema-examples.md`](docs/superpowers/appendices/G-db-schema-examples.md) | Flyway 脚本示例 |
| 安全详细设计 | [`docs/superpowers/appendices/H-security-detailed-design.md`](docs/superpowers/appendices/H-security-detailed-design.md) | 等保三级逐条对应 |
| ADR | [`docs/superpowers/adr/`](docs/superpowers/adr/) | 15 份架构决策记录 |

---

## 🧠 ADR 关键决策一览

| 编号 | 决策 | 概要 |
|---|---|---|
| 0001 | 注册中心与配置中心 | Nacos |
| 0002 | 数据库部署 | 单库 `lumen_db` + 业务前缀 |
| 0003 | 多租户隔离 | 行级（可升级库级） |
| 0004 | 工作流 | 自研双引擎（BPMN + DSL） |
| 0005 | 移动 App | Flutter / uni-app 跨端 |
| 0006 | 分布式事务 | Seata AT 模式 |
| 0007 | 消息队列 | RocketMQ |
| 0008 | 搜索 | Elasticsearch |
| 0009 | DB 迁移 | Flyway |
| 0010 | API 响应与错误码 | 统一封装 + 业务码分桶 |
| 0011 | 数据权限 | 5 级（全部/本部门及下/本部门/本人/自定义） |
| 0012 | 缓存 | 三级（Caffeine + Redis + 本地二级） |
| 0013 | 集成层 | Adapter 抽象 + 多实现可插拔 |
| 0014 | 发布策略 | 灰度（金丝雀）+ 蓝绿 |
| 0015 | 全链路追踪 | SkyWalking |

完整 ADR 见 [`docs/superpowers/adr/`](docs/superpowers/adr/)。

---

## 🔒 合规与安全

满足**等保三级**五大要求：

| 要求 | 实现 |
|---|---|
| 安全通信网络 | 全站 HTTPS + TLS 1.3；内部服务 mTLS |
| 安全区域边界 | Gateway 统一入口 + WAF 规则 + 黑白名单 |
| 安全计算环境 | RBAC + 数据权限 + 字段级加密 + 脱敏 |
| 安全管理中心 | 审计日志（6 类事件全覆盖）+ 集中日志 + 告警 |
| 安全运维管理 | 双因素 + 操作审计 + 漏洞扫描 + 渗透测试 |

详细方案：[`docs/superpowers/appendices/H-security-detailed-design.md`](docs/superpowers/appendices/H-security-detailed-design.md)

---

## 🗺️ Roadmap

### v1.0（当前）
- [x] 顶层架构 Spec
- [x] 15 份 ADR
- [x] 8 份附录
- [ ] 基础平台 5 个模块开发（租户 / 认证 / 通知 / 文件 / 字典）
- [ ] 组织架构 + HR + 薪资

### v2.0
- [ ] 财务 / 采购 / 合同 / 库存
- [ ] 工作流引擎自研落地
- [ ] SaaS 多租户灰度发布

### v3.0
- [ ] BI 报表 + 数仓
- [ ] 移动 App + 钉钉 / 企微 / 飞书
- [ ] 等保三级正式测评

---

## 🤝 贡献指南

1. Fork 仓库 → 创建 feature 分支（`feat/<module>-<short-desc>`）
2. 提交前：本地跑通 `mvn verify` 与 `pnpm build && pnpm lint`
3. 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)
4. 提交 PR 至 `main`，填写 PR 模板，关联对应 ADR / Spec
5. 至少 1 位架构组成员 + 1 位模块 Owner Review 后合并

---

## 📄 许可证

本项目基于 **MIT License** 开源，详见 [`LICENSE`](LICENSE)。

衍生说明：
- 后端基座 [RuoYi-Cloud](https://gitee.com/y_project/RuoYi-Cloud)（MIT）
- 前端基座 [RuoYi-Vue3](https://gitee.com/y_project/RuoYi-Vue)（MIT）

---

## 📬 联系方式

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/leiak/Lumen-AI-Company-SAAS |
| Issues | https://github.com/leiak/Lumen-AI-Company-SAAS/issues |
| Owner | @leiak |

---

> Built with ❤️ for modern enterprise teams — 打造中国式数字化经营底座