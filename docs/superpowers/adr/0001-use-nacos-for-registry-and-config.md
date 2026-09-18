# ADR-0001: 服务注册与配置中心选型 Nacos

## 状态
2026-09-18 已决策

## 背景
17 个微服务 + 6 个基础服务需要统一的：
- 服务注册与发现
- 配置中心（动态配置、灰度发布、多环境隔离）
- 健康检查

## 决策
使用 **Nacos 2.x** 作为服务注册与配置中心。

## 备选方案

| 方案 | 优点 | 缺点 | 否决理由 |
|---|---|---|---|
| **Eureka 2.x** | Spring Cloud 原生 | 已停维（2.0 后无更新） | 不再维护 |
| **Consul** | 多数据中心、ACL 强 | 中文文档少、Java 生态不如 Nacos | 团队学习成本 |
| **Apollo** | 配置中心能力强 | 注册功能弱、与 Spring Cloud 集成需额外胶水 | 配置中心首选 Nacos |
| **ZooKeeper** | 成熟稳定 | CP 模型不适合大规模服务发现；无配置中心能力 | 不适合微服务 |

## 后果

### 优点
- 注册 + 配置二合一，减少组件数
- 命名空间（Namespace）天然支持多租户隔离
- 与 Spring Cloud Alibaba 全家桶无缝集成
- Dashboard 开箱即用
- 支持配置变更监听 + 热更新

### 缺点
- 强依赖 Alibaba 生态
- 与 Spring Cloud 原生组件（如 OpenFeign、Ribbon）需注意版本兼容性

### 风险与缓解
- **风险**：未来若彻底去 Spring Cloud，转 K8s 需迁移
- **缓解**：K8s Service + ConfigMap + Spring Cloud Kubernetes 可平滑替代；Nacos 配置文件已 Git 化管理

## 相关决策
- ADR-0006 Seata AT 模式
- ADR-0014 灰度发布策略
