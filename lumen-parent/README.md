# Lumen 企业管理平台

Lumen 是一套企业级管理业务系统，覆盖人力、财务、合同、采购、资产等核心模块。

## 技术栈

- Spring Boot 3.2 + Spring Cloud 2023 + Spring Cloud Alibaba 2023
- Nacos 2.3 (注册/配置中心)
- MySQL 8 + MyBatis-Plus + Flyway
- Redis 7 + Caffeine (多级缓存)
- Spring Cloud Gateway + Sentinel (网关限流)
- Spring Security 6 + JWT (认证授权)
- SkyWalking 9 (链路追踪)
- Docker + Kubernetes (部署)

## 模块结构

| 模块 | 端口 | 说明 |
|---|---|---|
| lumen-gateway | 8080 | 统一网关 |
| lumen-auth | 9200 | 认证服务 |
| lumen-system | 9201 | 系统服务（用户/角色/菜单/部门） |
| lumen-platform | 9202 | 平台服务（租户/套餐/字典/序列） |
| lumen-org | 9203 | 组织服务（部门扩展/职级/汇报线/编制） |

## 本地开发

```bash
# 1. 启动基础设施
cd docker && docker compose up -d

# 2. 编译
mvn clean install

# 3. 启动服务（按顺序）
mvn spring-boot:run -pl lumen-gateway
mvn spring-boot:run -pl lumen-auth
mvn spring-boot:run -pl lumen-system
mvn spring-boot:run -pl lumen-platform
mvn spring-boot:run -pl lumen-org
```

## 默认账号

- 租户编码: default
- 用户名: admin
- 密码: admin123
