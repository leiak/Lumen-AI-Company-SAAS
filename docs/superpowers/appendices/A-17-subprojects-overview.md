# 附录 A：17 个子项目详细概览

> 本文档为顶层 spec 的延伸。每个子项目后续会单独产出 `design.md`、`plan.md`。
> 本附录给出**统一粒度的概览**：目标、场景、实体、接口、事件、验收。

---

## A.1 platform-base（平台基础改造）—— P0 · 5 周

### 目标
在 RuoYi-Cloud 上完成五大基础设施改造：
1. 多租户基础设施（TenantContext、Interceptor、Switch）
2. 统一异常/响应/日志体系
3. 接口幂等 + 防重放
4. 代码生成器增强（DDD 风格）
5. CI/CD Pipeline 基线

### 核心场景
- 新建租户、切换租户模式
- 开发第一个业务模块
- 跑通单元测试 + 集成测试
- PR 触发 CI，merge 触发 CD

### 关键实体

| 表 | 关键字段 | 说明 |
|---|---|---|
| `tenant` | id, code, name, type, expire_at, status | 租户主表 |
| `tenant_package` | id, code, name, modules(json), quota(json) | 套餐 |
| `tenant_config` | tenant_id, key, value | 租户级配置 |
| `common_seq` | name, current_val, step | 业务序列号 |

### 主要接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/platform/health` | GET | 健康检查 |
| `/platform/tenant/list` | GET | 租户列表（超管） |
| `/platform/tenant/create` | POST | 创建租户 |
| `/platform/tenant/{id}/switch-mode` | POST | 切换部署模式 |
| `/platform/generator/preview` | POST | 代码生成预览 |

### 关键事件
- `TenantCreatedEvent`
- `TenantSwitchedEvent`

### 验收标准
- [ ] `docker-compose up` 一键启动 platform + auth + gateway
- [ ] 多租户开关切换演示（private ↔ saas）
- [ ] 单元测试覆盖率 ≥ 70%
- [ ] CI/CD Pipeline 跑通（PR 自动部署 test）
- [ ] 17 个 starter 模块（`lumen-common-*`）可被业务服务引用

---

## A.2 auth-rbac（认证授权）—— P0 · 3 周

### 目标
完整 RBAC + 5 级数据权限 + SSO 适配器 + MFA + 登录审计。

### 核心场景
- 用户登录（账号密码 + 验证码 + MFA）
- 钉钉/企微扫码登录
- 角色分配 + 权限授予
- 部门数据权限过滤
- 登录失败锁定
- 密码策略强制
- 操作审计

### 关键实体（RuoYi 扩展）

| 表 | 扩展字段 | 说明 |
|---|---|---|
| `sys_user` | + tenant_id, data_scope, mfa_secret, last_pwd_change | 用户 |
| `sys_role` | + data_scope, custom_dept_ids | 角色 |
| `sys_menu` | + api_pattern | 菜单/权限 |
| `sys_user_role` | - | 用户角色关联 |
| `sys_role_dept` | - | 角色部门 |
| `sys_sso_account` | id, user_id, platform, open_id | SSO 绑定 |
| `sys_user_session` | id, user_id, token, ip, ua, expired_at | 会话 |

### 主要接口

| 接口 | 说明 |
|---|---|
| `/auth/login` | 账号密码登录 |
| `/auth/mfa/bind` | 绑定 MFA |
| `/mfa/verify` | 验证 MFA |
| `/auth/sso/{platform}/url` | 获取 SSO 授权 URL |
| `/auth/sso/{platform}/callback` | SSO 回调 |
| `/auth/logout` | 登出 |
| `/auth/refresh` | 刷新 Token |
| `/auth/user/profile` | 当前用户信息 |
| `/auth/user/change-password` | 改密 |

### 关键事件
- `UserLoggedInEvent`
- `UserLoggedOutEvent`
- `PasswordChangedEvent`
- `RoleAssignedEvent`
- `LoginFailedEvent`

### 验收标准
- [ ] 5 级 data_scope 全场景测试通过
- [ ] 钉钉 SSO 扫码登录完整跑通
- [ ] 登录失败 5 次锁定 30 分钟
- [ ] 密码 90 天强制更换
- [ ] 管理员 MFA 强制开启
- [ ] 所有登录/操作写入审计日志

---

## A.3 gateway（统一网关）—— P0 · 2 周

### 目标
统一入口 + 七道关卡：路由/限流/灰度/熔断/签名/防重放/审计。

### 关键实体

| 表 | 说明 |
|---|---|
| `gateway_route_config` | 路由配置 |
| `gateway_rate_limit_rule` | 限流规则 |
| `gateway_gray_rule` | 灰度规则 |

### 主要接口（内部管理）
- `/admin/gateway/route/list`
- `/admin/gateway/rate-limit/save`
- `/admin/gateway/gray-rule/save`

### 验收标准
- [ ] 1k QPS 压测 P99 < 50ms
- [ ] Sentinel 限流触发后降级返回
- [ ] 灰度 1% → 100% 全流程
- [ ] HMAC 签名校验内部高敏感接口

---

## A.4 system-mgmt（系统管理）—— P0 · 1 周

### 目标
基于 RuoYi 增强：字典、配置、定时任务、审计、在线用户。

### 核心场景
- 字典维护 + 级联
- 参数配置 + 热更新
- 定时任务 CRUD
- 在线用户查询 + 强踢
- 操作日志查询
- 登录日志查询

### 关键实体（全部复用 RuoYi）
- `sys_dict_type`、`sys_dict_data`、`sys_config`、`sys_job`、`sys_job_log`、`sys_oper_log`、`sys_logininfor`

### 主要接口
- `/system/dict/list`
- `/system/config/get`
- `/system/job/start`、`/system/job/stop`
- `/system/online/list`、`/system/online/force-logout`
- `/system/oper-log/list`
- `/system/logininfor/list`

### 验收标准
- [ ] 字典变更实时生效（带缓存刷新）
- [ ] 配置变更热更新（监听 Nacos Config）
- [ ] 定时任务失败告警
- [ ] 操作日志可按模块/用户/时间检索

---

## A.5 file-storage（文件存储）—— P0 · 2 周

### 目标
MinIO/OSS 抽象 + 分片上传 + 预览 + 分享。

### 核心场景
- 上传（普通 + 分片 + 秒传）
- 下载
- 预览（PDF/图片/Office）
- 分享（链接 + 提取码 + 有效期）
- 删除/恢复

### 关键实体

| 表 | 说明 |
|---|---|
| `file_info` | id, name, path, size, md5, biz_type, biz_id, owner_id, tenant_id |
| `file_share` | id, file_id, code, expire_at, password, download_count |
| `file_chunk` | id, upload_id, index, path, md5 |

### 主要接口
- `/file/upload`
- `/file/upload/chunk`
- `/file/{id}/download`
- `/file/{id}/preview`
- `/file/{id}/share/create`
- `/file/share/{code}/download`

### 验收标准
- [ ] 1GB 大文件分片上传
- [ ] PDF/图片/Office 预览（用 OpenOffice/LibreOffice 转换）
- [ ] 分享链接 + 提取码 + 有效期
- [ ] 跨租户文件 0 泄露

---

## A.6 message-center（消息中心）—— P0 · 3 周

### 目标
站内信 + 邮件 + 短信 + 钉钉推送 + WebSocket 实时。

### 核心场景
- 系统通知
- 审批结果通知
- 验证码
- 营销消息
- WebSocket 实时推送

### 关键实体

| 表 | 说明 |
|---|---|
| `msg_template` | id, code, channel, content, params, status |
| `msg_log` | id, tpl_code, receiver, channel, status, sent_at, error |
| `msg_subscription` | user_id, event_type, channel, enabled |

### 主要接口
- `/msg/send`
- `/msg/template/list`、`/msg/template/save`
- `/msg/log/list`
- `/msg/websocket/connect`（WS）

### 关键事件
- `MessageSentEvent`、`MessageReadEvent`

### 验收标准
- [ ] 多通道发送成功率 > 95%
- [ ] 消息追踪（投递状态可视化）
- [ ] WebSocket 1w 并发连接
- [ ] 模板变量渲染 + i18n

---

## A.7 workflow-engine（通用审批引擎）—— P1 · 6 周

### 目标
双引擎：自研状态机 + Flowable 7.x，统一 `ApprovalEngine` 接口。

### 核心场景
- 请假审批、转正审批、报销审批
- 采购定标（复杂 BPMN）
- 合同会签
- 转办、加签、催办、驳回
- 流程设计器可视化

### 关键实体

| 表 | 说明 |
|---|---|
| `wf_definition` | id, key, name, version, bpmn_xml, status |
| `wf_instance` | id, def_key, biz_key, biz_type, status, start_user, start_at |
| `wf_task` | id, inst_id, node_key, assignee, status, claim_at, complete_at |
| `wf_task_history` | 任务历史（完整审批轨迹） |
| `wf_cc_record` | 抄送记录 |
| `wf_form_schema` | 流程表单 Schema |

### 主要接口
- `/workflow/def/save`
- `/workflow/def/{key}/deploy`
- `/workflow/process/start`
- `/workflow/task/todo`
- `/workflow/task/approve`
- `/workflow/task/reject`
- `/workflow/task/transfer`
- `/workflow/task/add-sign`
- `/workflow/task/cc`
- `/workflow/inst/{id}/detail`

### 关键事件
- `ProcessStartedEvent`
- `TaskAssignedEvent`
- `TaskApprovedEvent`
- `TaskRejectedEvent`
- `ProcessCompletedEvent`

### 验收标准
- [ ] 简单审批自研状态机（< 50 行代码）
- [ ] 复杂 BPMN 走 Flowable（会签、或签、子流程）
- [ ] 流程设计器可视化（LogicFlow + BPMN 互转）
- [ ] 待办中心统一视图（自研 + Flowable 合并）

---

## A.8 org-structure（组织架构）—— P1 · 3 周

### 目标
部门树 + 岗位 + 职级 + 编制 + 汇报线 + 可视化组织图。

### 核心场景
- 部门 CRUD
- 部门拖拽调整（保留历史）
- 岗位管理
- 职级管理
- 编制管理（部门人数控制）
- 汇报线维护（虚线/实线）
- 组织图可视化

### 关键实体

| 表 | 说明 |
|---|---|
| `sys_dept` (复用) | + path（物化路径）, ancestors |
| `org_dept_relation` | 部门关系（多对多，矩阵组织） |
| `org_position_level` | 职级体系 |
| `org_reporting_line` | employee_id, manager_id, type(实线/虚线), level |
| `org_headcount` | dept_id, position_id, plan_count, actual_count |

### 主要接口
- `/org/dept/tree`
- `/org/dept/save`
- `/org/dept/{id}/move`
- `/org/post/list`
- `/org/level/list`
- `/org/reporting/set`
- `/org/headcount/list`

### 关键事件
- `DeptCreatedEvent`、`DeptMovedEvent`、`ReportingLineChangedEvent`

### 验收标准
- [ ] 组织树拖拽 + 历史保留
- [ ] 递归查询 5 级部门 < 100ms
- [ ] 汇报线环路检测
- [ ] 编制超编拦截

---

## A.9 hr（人力资源）—— P2 · 8 周

### 目标
完整 HR 模块：员工档案、招聘、入职、转正、调岗、离职、绩效、培训、合同。

### 核心场景
- 招聘漏斗（JD → 简历 → 面试 → Offer）
- 入职流程（多级审批）
- 试用期跟踪
- 转正申请
- 调岗/晋升
- 离职交接（资产归还 + 权限回收）
- 绩效周期（KPI/360）
- 培训计划
- 合同管理（与 contract 模块联动）

### 关键实体

| 表 | 说明 |
|---|---|
| `hr_employee` | id, user_id, code, name, id_card, mobile, hire_date, dept_id, post_id, level_id, status |
| `hr_recruit_job` | JD 招聘需求 |
| `hr_recruit_candidate` | 候选人 |
| `hr_offer` | Offer 记录 |
| `hr_onboarding` | 入职清单 |
| `hr_transfer` | 调岗记录 |
| `hr_resignation` | 离职申请 |
| `hr_performance_cycle` | 绩效周期 |
| `hr_performance_score` | 绩效评分 |
| `hr_training_plan` | 培训计划 |
| `hr_training_record` | 培训记录 |

### 主要接口
- `/hr/employee/list`、`/hr/employee/{id}`
- `/hr/employee/save`、`/hr/employee/transfer`
- `/hr/recruit/job/create`、`/hr/recruit/candidate/add`
- `/hr/offer/send`
- `/hr/onboarding/start`
- `/hr/performance/cycle/start`、`/hr/performance/submit`
- `/hr/training/plan/list`
- `/hr/resignation/submit`

### 关键事件
- `EmployeeCreatedEvent`
- `EmployeeTransferredEvent`
- `EmployeeResignedEvent`
- `PerformanceCompletedEvent`

### 验收标准
- [ ] 招聘 → 入职 → 转正 → 调岗 → 离职全流程
- [ ] 试用期到期提醒
- [ ] 合同到期 30/15/7 天提醒
- [ ] 离职自动回收资产 + 权限
- [ ] 绩效评分计算正确

---

## A.10 finance（财务管理）—— P2 · 10 周

### 目标
完整财务模块：科目、凭证、总账、应收应付、报销、预算、发票。

### 核心场景
- 凭证录入/过账/反过账
- 月结/年结
- 应收单/收款单核销
- 应付单/付款单
- 报销申请/审批/打款
- 预算编制/控制
- 发票录入/勾选认证
- 三大报表（资产负债表/利润表/现金流量表）

### 关键实体

| 表 | 说明 |
|---|---|
| `fin_account_subject` | 科目（树形） |
| `fin_voucher` | 凭证 |
| `fin_voucher_entry` | 凭证明细 |
| `fin_receivable` | 应收单 |
| `fin_payable` | 应付单 |
| `fin_expense_report` | 报销单 |
| `fin_payment` | 付款单 |
| `fin_budget` | 预算 |
| `fin_budget_item` | 预算明细 |
| `fin_invoice` | 发票 |
| `fin_period` | 会计期间 |

### 主要接口
- `/fin/voucher/save`、`/fin/voucher/post`、`/fin/voucher/{id}/reverse`
- `/fin/receivable/list`、`/fin/receivable/{id}/collect`
- `/fin/payable/list`、`/fin/payable/{id}/pay`
- `/fin/expense/submit`、`/fin/expense/{id}/approve`
- `/fin/budget/save`、`/fin/budget/check`
- `/fin/invoice/save`、`/fin/invoice/recognize`
- `/fin/report/balance-sheet`
- `/fin/report/income-statement`
- `/fin/report/cash-flow`

### 关键事件
- `VoucherPostedEvent`
- `ReceivableCreatedEvent`
- `ExpenseApprovedEvent`
- `BudgetExceededEvent`
- `InvoiceRecognizedEvent`

### 验收标准
- [ ] 借贷平衡校验（凭证保存时）
- [ ] 资产负债表平衡校验
- [ ] 预算超支拦截
- [ ] 月结流程完整（结转损益）
- [ ] 发票 OCR 自动识别字段

---

## A.11 assets（资产管理）—— P2 · 5 周

### 目标
资产全生命周期：卡片、领用、调拨、盘点、折旧；车辆/印章/证照专项管理。

### 核心场景
- 资产建卡（编号、分类、原值）
- 资产领用申请
- 资产调拨（跨部门）
- 资产盘点（按部门/按分类）
- 折旧计提（月结时自动）
- 资产报废
- 车辆调度、里程记录
- 印章用印申请、记录

### 关键实体

| 表 | 说明 |
|---|---|
| `ast_asset` | id, code, name, category_id, original_value, current_value, dept_id, custodian_id, status |
| `ast_category` | 资产分类 |
| `ast_custody` | 领用记录 |
| `ast_transfer` | 调拨记录 |
| `ast_stocktake` | 盘点单 |
| `ast_depreciation` | 折旧明细 |
| `ast_vehicle` | 车辆扩展 |
| `ast_seal` | 印章 |
| `ast_seal_usage` | 用印记录 |
| `ast_certificate` | 证照（营业执照/资质） |

### 主要接口
- `/assets/list`、`/assets/save`
- `/assets/custody/apply`、`/assets/custody/{id}/approve`
- `/assets/transfer`
- `/assets/stocktake/start`、`/assets/stocktake/{id}/submit`
- `/assets/depreciation/run`
- `/assets/vehicle/schedule`
- `/assets/seal/use`

### 关键事件
- `AssetCreatedEvent`
- `AssetTransferredEvent`
- `DepreciationCalculatedEvent`
- `SealUsedEvent`

### 验收标准
- [ ] 折旧月结自动计提（多种折旧方法）
- [ ] 盘点差异调整流程
- [ ] 车辆调度 + 里程记录
- [ ] 印章用印审计闭环

---

## A.12 procurement（采购管理）—— P3 · 6 周

### 目标
采购全流程：供应商、询比价、招投标、采购单、收货、付款。

### 核心场景
- 供应商准入评估
- 询价单发起（多供应商）
- 比价分析（自动选最低/最优）
- 招投标流程（公开/邀请）
- 采购单审批
- 收货入库
- 对账
- 付款申请

### 关键实体

| 表 | 说明 |
|---|---|
| `proc_supplier` | id, code, name, tax_no, level, status |
| `proc_supplier_qualification` | 资质附件 |
| `proc_inquiry` | 询价单 |
| `proc_quotation` | 报价单 |
| `proc_bidding` | 招投标 |
| `proc_order` | 采购单 |
| `proc_order_item` | 采购单明细 |
| `proc_receipt` | 收货单 |
| `proc_payment` | 付款单 |

### 主要接口
- `/proc/supplier/list`、`/proc/supplier/save`
- `/proc/inquiry/create`、`/proc/inquiry/{id}/quote`
- `/proc/order/create`、`/proc/order/{id}/approve`
- `/proc/receipt/create`、`/proc/receipt/{id}/confirm`
- `/proc/payment/apply`

### 关键事件
- `SupplierCreatedEvent`
- `OrderApprovedEvent`
- `GoodsReceivedEvent`
- `PaymentCompletedEvent`

### 验收标准
- [ ] 询比价自动比价分析
- [ ] 采购入库触发财务应付 + 库存入库
- [ ] 三方比价可视化
- [ ] 供应商评级自动计算

---

## A.13 contract（合同管理）—— P3 · 6 周

### 目标
合同全生命周期：起草、审批、电子签、履约、到期、归档。

### 核心场景
- 合同起草（模板填充）
- 合同审批（多级会签）
- 电子签章（法大大/契约锁/e签宝）
- 履约跟踪（付款计划、交付节点）
- 合同变更
- 到期提醒（30/15/7 天）
- 合同归档

### 关键实体

| 表 | 说明 |
|---|---|
| `ctr_contract` | id, no, title, party_a, party_b, type, amount, signed_at, status |
| `ctr_clause_template` | 合同模板 |
| `ctr_clause` | 合同条款（关联模板实例） |
| `ctr_payment_plan` | 收款/付款计划 |
| `ctr_fulfillment` | 履约记录 |
| `ctr_attachment` | 附件 |
| `ctr_change_log` | 变更日志 |
| `ctr_sign_task` | 签署任务 |

### 主要接口
- `/contract/save`、`/contract/draft`
- `/contract/{id}/submit`、`/contract/{id}/approve`
- `/contract/{id}/sign/create`
- `/contract/{id}/sign/status`
- `/contract/{id}/payment-plan`
- `/contract/{id}/fulfillment/record`
- `/contract/expiring`
- `/contract/{id}/archive`

### 关键事件
- `ContractDraftedEvent`
- `ContractApprovedEvent`
- `ContractSignedEvent`
- `ContractExpiringEvent`（延迟消息）
- `ContractArchivedEvent`

### 验收标准
- [ ] 电子签对接（默认契约锁）
- [ ] 到期 30/15/7 天自动提醒
- [ ] 收款计划自动生成财务凭证
- [ ] 合同检索走 ES（标题/条款/全文）

---

## A.14 inventory（库存管理）—— P3 · 5 周

### 目标
库存管理：入库、出库、调拨、盘点、安全库存预警。

### 核心场景
- 多仓库管理
- 入库（采购入库/其他入库）
- 出库（销售出库/领用出库）
- 调拨（跨仓库）
- 盘点（全盘/抽盘）
- 安全库存预警
- 库存账期管理

### 关键实体

| 表 | 说明 |
|---|---|
| `inv_warehouse` | 仓库 |
| `inv_location` | 库位 |
| `inv_item` | SKU 主数据 |
| `inv_stock` | 库存（按仓库+SKU+批次） |
| `inv_inout` | 出入库单 |
| `inv_inout_item` | 出入库明细 |
| `inv_transfer` | 调拨单 |
| `inv_stocktake` | 盘点单 |
| `inv_safety_stock` | 安全库存规则 |

### 主要接口
- `/inv/stock/list`
- `/inv/inout/create`、`/inv/inout/{id}/confirm`
- `/inv/transfer/create`、`/inv/transfer/{id}/confirm`
- `/inv/stocktake/start`
- `/inv/safety/warning`

### 关键事件
- `StockInEvent`、`StockOutEvent`、`StockTransferredEvent`、`SafetyStockAlertEvent`

### 验收标准
- [ ] 多仓库 + 多库位管理
- [ ] 先进先出（FIFO）支持
- [ ] 安全库存触发告警
- [ ] 与采购/销售模块联动（事件驱动）

---

## A.15 sales-crm（销售 + CRM）—— P3 · 6 周

### 目标
销售 + CRM：客户、商机、报价、订单、回款、对账。

### 核心场景
- 客户建档（公海/私海）
- 联系人管理
- 销售线索（Lead）
- 销售机会（Opportunity）+ 漏斗
- 报价单（多版本）
- 销售订单
- 发货
- 回款登记
- 对账单

### 关键实体

| 表 | 说明 |
|---|---|
| `sal_customer` | id, code, name, level, source, owner_user_id, status |
| `sal_contact` | 联系人 |
| `sal_lead` | 销售线索 |
| `sal_opportunity` | 销售机会（含阶段、金额、概率） |
| `sal_quotation` | 报价单 |
| `sal_order` | 销售订单 |
| `sal_order_item` | 订单明细 |
| `sal_shipment` | 发货单 |
| `sal_receivable` | 应收账款 |
| `sal_payment_record` | 回款记录 |
| `sal_statement` | 对账单 |

### 主要接口
- `/sal/customer/list`、`/sal/customer/save`
- `/sal/opportunity/create`、`/sal/opportunity/{id}/stage-update`
- `/sal/quotation/send`、`/sal/quotation/{id}/accept`
- `/sal/order/create`、`/sal/order/{id}/confirm`
- `/sal/shipment/create`
- `/sal/receivable/collect`
- `/sal/statement/generate`

### 关键事件
- `CustomerCreatedEvent`
- `OrderConfirmedEvent`
- `GoodsShippedEvent`
- `PaymentReceivedEvent`

### 验收标准
- [ ] 销售漏斗可视化
- [ ] 订单 → 出库 → 应收 → 回款闭环
- [ ] 对账单自动生成 + 邮件发送
- [ ] 客户公海/私海规则自动回收

---

## A.16 payroll（薪资管理）—— P4 · 6 周

### 目标
薪资全流程：算薪、个税、社保公积金、银行报盘、工资条。

### 核心场景
- 薪资结构配置
- 月度算薪（考勤联动）
- 个税计算（累计预扣预缴）
- 社保公积金
- 银行代发文件生成
- 工资条发放
- 个税申报

### 关键实体

| 表 | 说明 |
|---|---|
| `pay_salary_structure` | 薪资结构模板 |
| `pay_employee_salary` | 员工薪资档案 |
| `pay_slip` | 工资条 |
| `pay_slip_item` | 工资条明细 |
| `pay_tax` | 个税记录 |
| `pay_social_security` | 社保 |
| `pay_bank_file` | 银行报盘文件 |
| `pay_payslip_read` | 工资条已读记录 |

### 主要接口
- `/pay/structure/save`
- `/pay/employee-salary/save`
- `/pay/calc/run`
- `/pay/slip/list`
- `/pay/bank-file/generate`
- `/pay/payslip/{id}/employee-view`
- `/pay/tax/declare`

### 关键事件
- `PayrollCalculatedEvent`
- `PayslipGeneratedEvent`
- `BankFileGeneratedEvent`

### 验收标准
- [ ] 算薪准确（与 HR 考勤联动）
- [ ] 个税累计算正确（专项附加扣除支持）
- [ ] 银行文件格式正确（建行/工行/招行适配）
- [ ] 工资条隐私保护（仅本人可见）

---

## A.17 bi-reporting（BI 报表）—— P4 · 8 周

### 目标
数据仓库 + 指标体系 + 可视化大屏。

### 核心场景
- 数据仓库同步（MySQL → Doris）
- 指标体系管理
- 看板配置（拖拽式）
- 预定义大屏（销售/财务/HR/采购）
- Ad-hoc 查询
- 报表导出

### 关键实体

| 表 | 说明 |
|---|---|
| `bi_metric` | 指标定义（口径、维度） |
| `bi_dataset` | 数据集（SQL 配置） |
| `bi_dashboard` | 看板 |
| `bi_widget` | 看板组件 |
| `bi_report` | 报表 |
| `bi_permission` | 报表权限 |

### 主要接口
- `/bi/dashboard/list`、`/bi/dashboard/save`
- `/bi/metric/query`
- `/bi/report/export`
- `/bi/sync/trigger`

### 验收标准
- [ ] 实时同步延迟 < 5min
- [ ] 10 个核心看板上线（销售/财务/HR/采购）
- [ ] 指标口径可视化配置
- [ ] 报表权限与多租户隔离

---

## A.18 mobile-integration（移动端 + 协作集成）—— P4 · 8 周

### 目标
移动端 App + H5 + 钉钉/企微/飞书工作台集成。

### 核心场景
- App 登录（手机号/SSO）
- 审批中心（移动审批）
- 考勤打卡（GPS + 人脸）
- 消息推送
- 通讯录同步
- 钉钉工作台 + 审批回调
- 企微应用 + 消息

### 关键实体

| 表 | 说明 |
|---|---|
| `mobile_app_version` | id, platform, version, force_update, release_notes |
| `mobile_push_token` | id, user_id, platform, token |
| `integration_app_config` | 平台应用配置 |
| `integration_event_log` | 集成事件日志 |

### 主要接口（App 端）
- `/mobile/api/auth/login`
- `/mobile/api/workflow/todo`
- `/mobile/api/attendance/clock`
- `/mobile/api/notification/list`

### 集成回调
- `/integration/dingtalk/sso/callback`
- `/integration/dingtalk/approval/callback`
- `/integration/wechatwork/event`

### 关键事件
- `AppVersionUpdatedEvent`
- `IntegrationConfigChangedEvent`

### 验收标准
- [ ] App 登录（手机号验证码）
- [ ] 审批闭环（移动端发起 → 桌面端审批 → 移动端通知）
- [ ] 钉钉工作台 + 审批回调
- [ ] 推送到达率 > 95%

---

## 附录 A 总结

| 子项目 | 阶段 | 工期 | 实体数 | 接口数 | 关键依赖 |
|---|---|---|---|---|---|
| platform-base | P0 | 5w | 4 | 5 | RuoYi |
| auth-rbac | P0 | 3w | 7 | 9 | platform |
| gateway | P0 | 2w | 3 | 3 | platform |
| system-mgmt | P0 | 1w | 7 | 6 | platform |
| file-storage | P0 | 2w | 3 | 6 | platform |
| message-center | P0 | 3w | 3 | 4 | platform |
| workflow-engine | P1 | 6w | 6 | 10 | platform |
| org-structure | P1 | 3w | 5 | 7 | platform |
| hr | P2 | 8w | 11 | 8 | org, workflow |
| finance | P2 | 10w | 11 | 9 | workflow |
| assets | P2 | 5w | 10 | 7 | org, workflow |
| procurement | P3 | 6w | 9 | 5 | finance, assets |
| contract | P3 | 6w | 8 | 8 | workflow, file |
| inventory | P3 | 5w | 9 | 5 | procurement, sales |
| sales-crm | P3 | 6w | 11 | 7 | finance, contract |
| payroll | P4 | 6w | 8 | 7 | hr, finance |
| bi-reporting | P4 | 8w | 6 | 4 | 全部业务库 |
| mobile-integration | P4 | 8w | 4 | 4 | auth, message, workflow |

**总计**：17 个子项目、约 130+ 张业务表、约 130+ 个核心接口、8 个核心时序事件。
