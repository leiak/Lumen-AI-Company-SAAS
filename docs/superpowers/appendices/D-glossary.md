# 附录 D：术语表（Glossary）

> 本术语表覆盖整个系统的关键名词、缩写、协议。
> 按字母顺序排列，便于跨团队沟通。

---

## A

### ADR（Architecture Decision Record）
**架构决策记录**。记录关键技术决策的背景、方案、后果。  
📁 `docs/superpowers/adr/`

### API（Application Programming Interface）
**应用程序编程接口**。本系统对外提供 RESTful API + OpenFeign。

### APM（Application Performance Management）
**应用性能管理**。本系统使用 SkyWalking。

### AT 模式（Automatic Transaction）
**Seata 自动事务模式**。通过 SQL 解析实现无侵入分布式事务。  
📄 详见 ADR-0006

---

## B

### BFF（Backend for Frontend）
**面向前端的后端**。为特定前端定制的 API 层。  
本系统：mobile-gateway 是 BFF。

### BPMN（Business Process Model and Notation）
**业务流程模型与标记法**。用于复杂审批流建模。  
本系统：workflow-engine 复杂场景使用 BPMN + Flowable。

---

## C

### CDC（Change Data Capture）
**变更数据捕获**。监听数据库 binlog，实时同步数据到下游。  
本系统：MySQL → Kafka → ES / Doris。

### CI/CD（Continuous Integration / Continuous Deployment）
**持续集成 / 持续部署**。本系统使用 Jenkins + JCasC。

### CQRS（Command Query Responsibility Segregation）
**命令查询职责分离**。读写分离架构模式。  
本系统：BI 数仓是典型 CQRS。

---

## D

### DDD（Domain-Driven Design）
**领域驱动设计**。一种软件设计方法。  
本系统：代码生成器支持 DDD 风格。

### DDL（Data Definition Language）
**数据定义语言**。CREATE/ALTER/DROP 等语句。  
本系统：所有 DDL 走 Flyway。

### DML（Data Manipulation Language）
**数据操作语言**。SELECT/INSERT/UPDATE/DELETE。  
本系统：所有 DML 走 MyBatis 拦截器。

### DTO（Data Transfer Object）
**数据传输对象**。层间数据传输用。

### DO（Domain Object）
**领域对象**。与数据库表一一对应。

### DML 拦截器
**MyBatis 拦截器**。本系统有多租户拦截器、数据权限拦截器、字段填充拦截器。

---

## E

### ELK（Elasticsearch + Logstash + Kibana）
**日志技术栈**。本系统用于日志采集与检索。

### ERP（Enterprise Resource Planning）
**企业资源计划**。本系统的整体定位。

### ES（Elasticsearch）
**分布式搜索引擎**。本系统用于全文检索。

---

## F

### FE / FS / FE / FBP
**前端相关缩写**，本系统统一为 `FE`。

### Flyway
**数据库迁移工具**。本系统统一 DDL 管理。  
📄 详见 ADR-0009

---

## G

### GDPR（General Data Protection Regulation）
**欧盟通用数据保护条例**。若系统服务海外客户需遵守。

### Grafana
**可视化监控仪表盘工具**。本系统用于指标可视化。

---

## H

### HSM（Hardware Security Module）
**硬件安全模块**。本系统密钥存储可对接 HSM。

### HIDS（Host-based Intrusion Detection System）
**主机入侵检测系统**。本系统推荐在生产部署 HIDS。

### HTTPS（HTTP Secure）
**HTTP 安全版本**。TLS 1.2+。本系统全站 HTTPS。

---

## I

### IAM（Identity and Access Management）
**身份与访问管理**。本系统的 auth-service 是 IAM。

### i18n（Internationalization）
**国际化**。本系统支持中/英双语。

---

## J

### JWT（JSON Web Token）
**JSON Web 令牌**。本系统使用 Access + Refresh 双 Token。  
📄 详见主 spec §6.2

### JCasC（Jenkins Configuration as Code）
**Jenkins 配置即代码**。Pipeline as Code。

---

## K

### KMS（Key Management Service）
**密钥管理服务**。本系统敏感字段加密的密钥管理。  
本系统：私有部署自研 + SaaS 用阿里云 KMS。

### K8s（Kubernetes）
**容器编排系统**。本系统生产环境部署在 K8s。

---

## L

### LB（Load Balancer）
**负载均衡器**。本系统双层 LB：Nginx + Ribbon。

### LDAP（Lightweight Directory Access Protocol）
**轻量目录访问协议**。本系统 SSO 可对接 LDAP。

### LSN（Log Sequence Number）
**日志序列号**。MySQL binlog 位置标识。

---

## M

### MFA（Multi-Factor Authentication）
**多因素认证**。本系统管理员强制开启。  
📄 详见主 spec §7.2.2

### MQ（Message Queue）
**消息队列**。本系统使用 RocketMQ。

### MTTR（Mean Time To Recovery）
**平均恢复时间**。本系统目标 < 30 分钟。

### MTBF（Mean Time Between Failures）
**平均故障间隔时间**。本系统目标 > 30 天。

---

## N

### Nacos
**服务注册与配置中心**。本系统统一使用 Nacos 2.x。  
📄 详见 ADR-0001

### NFR（Non-Functional Requirements）
**非功能性需求**。性能/可用性/安全等。  
📄 详见附录 C

---

## O

### OAuth 2.0
**开放授权协议**。本系统 SSO 实现遵循 OAuth 2.0。

### OIDC（OpenID Connect）
**基于 OAuth 2.0 的身份认证协议**。本系统 SSO 支持。

### OSS（Object Storage Service）
**对象存储服务**。本系统使用 MinIO（私有）/阿里云 OSS（SaaS）。

### OLTP（Online Transaction Processing）
**在线事务处理**。本系统业务库（MySQL）。

### OLAP（Online Analytical Processing）
**在线分析处理**。本系统数仓（Doris）。

---

## P

### PC（Personal Computer）
**个人电脑**。本系统 PC 端浏览器访问。

### P95 / P99
**第 95/99 百分位响应时间**。性能指标。

### PO（Persistent Object）
**持久化对象**。与数据库表对应。

### P0/P1/P2/P3/P4
**优先级**。本系统的子项目分阶段：
- P0 = 基础（最先做）
- P1 = 基础业务
- P2 = 核心业务
- P3 = 业务协同
- P4 = 高级能力

### Prometheus
**监控系统**。本系统用于指标采集。

---

## Q

### QPS（Queries Per Second）
**每秒查询数**。性能指标。

### QoS（Quality of Service）
**服务质量**。本系统对不同接口分级 QoS。

---

## R

### RBAC（Role-Based Access Control）
**基于角色的访问控制**。本系统用户-角色-权限模型。

### RDBMS（Relational Database Management System）
**关系数据库管理系统**。本系统使用 MySQL 8。

### REST（Representational State Transfer）
**表述性状态转移**。本系统 API 设计风格。

### RPO（Recovery Point Objective）
**恢复点目标**。本系统 ≤ 15 分钟。

### RTO（Recovery Time Objective）
**恢复时间目标**。本系统 ≤ 4 小时。

### Redis
**内存数据库**。本系统用于缓存 + 分布式锁。

---

## S

### SaaS（Software as a Service）
**软件即服务**。本系统支持 SaaS 多租户模式。  
📄 详见 ADR-0003

### SAML（Security Assertion Markup Language）
**安全断言标记语言**。本系统 SSO 可对接。

### Seata
**分布式事务框架**。本系统使用 AT 模式。  
📄 详见 ADR-0006

### SkyWalking
**链路追踪系统**。本系统统一使用。  
📄 详见 ADR-0015

### SLA（Service Level Agreement）
**服务等级协议**。本系统各服务有明确 SLA。  
📄 详见附录 C

### SM2 / SM3 / SM4
**国密算法**。本系统按需对接。

### SQL 注入
**SQL Injection**。本系统全部使用预编译参数化查询，无注入风险。

### SSO（Single Sign-On）
**单点登录**。本系统支持钉钉/企微/飞书 SSO。

---

## T

### TCC（Try-Confirm-Cancel）
**分布式事务模式**。本系统金融场景使用。  
📄 详见 ADR-0006

### TCC 模式 vs AT 模式
**对比**：

| 维度 | AT | TCC |
|---|---|---|
| 侵入性 | 无 | 高 |
| 性能 | 中 | 高 |
| 一致性 | 最终一致 | 强一致 |
| 适用 | 90% 业务 | 金融 |

### TLS（Transport Layer Security）
**传输层安全协议**。本系统 HTTPS 使用 TLS 1.2+。

### TPS（Transactions Per Second）
**每秒事务数**。性能指标。

### TraceID
**追踪 ID**。贯穿请求全链路的唯一标识。

---

## U

### UUID（Universally Unique Identifier）
**通用唯一识别码**。本系统业务单号生成。

---

## V

### VO（Value Object）
**值对象**。本系统 Controller ↔ Service 数据传输。

### VPC（Virtual Private Cloud）
**虚拟私有云**。本系统部署在 VPC 内。

---

## W

### WAF（Web Application Firewall）
**Web 应用防火墙**。本系统接入阿里云 WAF。

### WebSocket
**全双工通信协议**。本系统用于消息推送。

---

## X

### XSS（Cross-Site Scripting）
**跨站脚本攻击**。本系统前端框架 + CSP 防护。

### XML（Extensible Markup Language）
**可扩展标记语言**。MyBatis Mapper 用 XML。

---

## Y

### YAML
**YAML Ain't Markup Language**。本系统配置文件格式。

---

## Z

### Zipkin / Jaeger
**链路追踪系统**。SkyWalking 备选。

---

## 业务领域缩写

### OA（Office Automation）
**办公自动化**。本系统涵盖流程审批、公告、文档。

### BI（Business Intelligence）
**商业智能**。本系统 bi-reporting 模块。

### CRM（Customer Relationship Management）
**客户关系管理**。本系统 sales-crm 模块。

### ERP（Enterprise Resource Planning）
**企业资源计划**。本系统整体定位。

### HR（Human Resources）
**人力资源**。本系统 hr 模块。

### MES（Manufacturing Execution System）
**制造执行系统**。本系统**不做**。

### OA 与协同办公
本系统支持流程审批、文档协作。

### SCM（Supply Chain Management）
**供应链管理**。本系统 procurement + inventory + contract 共同支撑。

---

## 中间件/技术栈缩写

| 缩写 | 全称 | 用途 |
|---|---|---|
| MySQL | - | 关系数据库 |
| Redis | - | 缓存 |
| RocketMQ | - | 消息队列 |
| Nacos | - | 注册配置 |
| Sentinel | - | 限流熔断 |
| Seata | - | 分布式事务 |
| SkyWalking | - | 链路追踪 |
| MinIO | - | 对象存储 |
| Flowable | - | 流程引擎 |
| Doris | - | 实时分析库 |
| ES | Elasticsearch | 搜索引擎 |
| OSS | Object Storage Service | 对象存储 |
| CDN | Content Delivery Network | 内容分发网络 |
| ELK | Elasticsearch/Logstash/Kibana | 日志 |
| APM | Application Performance Management | 应用性能管理 |
| MVC | Model-View-Controller | 架构模式 |
| ORM | Object-Relational Mapping | 对象关系映射 |
| CAP | Consistency/Availability/Partition tolerance | 分布式理论 |
| BASE | Basically Available/Soft state/Eventual consistency | 分布式理论 |
| ACID | Atomicity/Consistency/Isolation/Durability | 数据库事务 |

---

## 业务术语

| 术语 | 含义 |
|---|---|
| **租户** | 一家使用本系统的公司（Tenant） |
| **超管** | 平台超级管理员（管理所有租户） |
| **租户管理员** | 单个租户内的最高权限管理员 |
| **用户** | 租户内的具体员工（绑定员工档案） |
| **部门** | 组织单元 |
| **岗位** | 职责定义（如"HR 专员"） |
| **职级** | 等级体系（如 P5、P6） |
| **汇报线** | 上下级关系（实线 + 虚线） |
| **编制** | 部门人数计划 |
| **员工档案** | 员工完整信息 |
| **入转调离** | 入职、 转正、调岗、离职 |
| **KPI** | 关键绩效指标 |
| **OKR** | 目标与关键成果 |
| **绩效周期** | 通常为季度或月度 |
| **360 评估** | 多维度评估 |
| **试用期** | 通常 3 个月 |
| **报销** | 员工费用报销 |
| **差旅** | 出差相关 |
| **预算** | 财务预算控制 |
| **凭证** | 财务记账凭证 |
| **过账** | 凭证生效 |
| **结转** | 月末/年末损益结转 |
| **应收/应付** | 应收/应付账款 |
| **资产卡片** | 固定资产记录 |
| **折旧** | 资产价值减少 |
| **盘点** | 资产实物核对 |
| **询比价** | 采购询价比价 |
| **定标** | 采购确定中标 |
| **招投标** | 公开/邀请招标 |
| **履约** | 合同执行 |
| **签署** | 合同电子签章 |
| **到货** | 采购物资到达 |
| **入库/出库** | 库存变动 |
| **调拨** | 跨仓库移动 |
| **库存预警** | 库存量低于安全线 |
| **CRM 公海** | 无人跟进的客户池 |
| **商机** | 销售机会 |
| **报价** | 销售报价 |
| **对账单** | 与客户/供应商核对账目 |
| **算薪** | 计算工资 |
| **个税** | 个人所得税 |
| **社保** | 社会保险 |
| **公积金** | 住房公积金 |
| **银行代发** | 银行代发工资 |
| **工资条** | 员工工资明细 |

---

## 文档与代码约定

| 项 | 约定 |
|---|---|
| 包名 | `com.lumen.*` |
| 表前缀 | `业务域_`（如 `hr_employee`） |
| 错误码 | 5 位数字（模块+类型+序号） |
| API 路径 | `/{module}/{resource}/{action}` |
| MQ Topic | `{module}.{event}.v{version}` |
| 缓存 Key | `lumen:{module}:{bizType}:{bizId}` |
| 状态枚举 | `{ENTITY}_{STATUS}` |
| Git 分支 | `main` / `feature/*` / `release/*` / `hotfix/*` |
| Tag 规范 | `v{yymmdd}.{seq}` 或 `v{semver}` |
| Commit 规范 | `<type>(scope): subject` |

---

**下一附录**：[附录 E：数据流示例](./E-data-flow-example.md)
