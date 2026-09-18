# Lumen 企业管理平台

Lumen 是一套企业级管理业务系统，覆盖人力、财务、合同、采购、资产等核心模块。

## 技术栈

- Spring Boot 3.2.5 + Spring Cloud 2023 + Spring Cloud Alibaba 2023
- Nacos 2.3 (注册/配置中心)
- MySQL 8 + MyBatis-Plus + Flyway
- Redis 7 + Caffeine (多级缓存)
- Spring Cloud Gateway (Reactive, WebFlux)
- Spring Security 6 + JWT (jjwt, HS256)
- Docker + Docker Compose

## 模块结构与端口分配（P1 实际分配）

| 模块 | 端口 | 上下文路径 | 说明 |
|---|---|---|---|
| lumen-gateway | 9200 | / | 统一网关 (Reactive) |
| lumen-platform | 9201 | /platform | 平台服务（租户/套餐/字典/序列） |
| lumen-auth | 9202 | /auth | 认证服务（登录/Token/用户信息） |
| lumen-system | 9203 | /system | 系统服务（用户/角色/菜单/部门） |
| lumen-org | 9204 | /org | 组织服务（部门扩展/职级/汇报线/编制） |

> 注：早期模块结构（lumen-system:9201 / lumen-platform:9202 / lumen-org:9203）已变更。P1
> 重排为 platform:9201 / auth:9202 / system:9203 / org:9204 / gateway:9200。

## 启动顺序

```
mysql  ->  redis  ->  nacos  ->  业务服务 (platform → auth → system → org)  ->  gateway
```

1. **MySQL** — `lumen-mysql` 容器初始化（自动执行 `sql/` 初始化脚本，Flyway 会按版本继续迁移）。
2. **Redis** — `lumen-redis` 容器（密码 `redis123`）。
3. **Nacos** — `lumen-nacos` 容器，命名空间 `lumen-public`，用户 `lumen/lumen`。
4. **业务服务** — 按 `platform → auth → system → org` 顺序启动，确保依赖方向正确。
5. **Gateway** — 最后启动，它需要从 Nacos 拉取所有业务服务实例。

## 本地启动

```bash
# 1. 启动基础设施（MySQL/Redis/Nacos）
cd docker && docker compose up -d

# 2. 编译（依赖 Java 17）
mvn clean install

# 3. 启动服务（每个服务独立终端）
mvn spring-boot:run -pl lumen-platform
mvn spring-boot:run -pl lumen-auth
mvn spring-boot:run -pl lumen-system
mvn spring-boot:run -pl lumen-org
mvn spring-boot:run -pl lumen-gateway
```

### JVM / 配置要求

- **Java 17**（必需，Spring Boot 3.x 与 jjwt 0.12+ 不再支持 Java 8/11）。
- **MySQL root 密码**：`root`（容器内 `MYSQL_ROOT_PASSWORD: root123`，但运行时通过
  `--spring.datasource.password=root` 覆盖；如不一致需修改）。
- **JWT Secret**：`lumen.security.jwt.secret` 至少 32 字节且不能以 `lumen-default-`
  开头（`JwtTokenProvider` 显式拒绝默认值）。
- **Nacos 命名空间**：`lumen-public`（手工创建于 `tenant_info` 表）。

## 服务健康检查端点

| 服务 | URL | 是否需鉴权 |
|---|---|---|
| Gateway | `http://localhost:9200/auth/health` | 否（白名单） |
| Auth | `http://localhost:9202/auth/health` | 否 |
| Platform | `http://localhost:9201/platform/health` | 否 |
| System | `http://localhost:9203/system/health` | 否 |
| Org | `http://localhost:9204/org/health` | 否 |

健康检查端点在网关的 `JwtAuthGlobalFilter` 白名单中，跳过 JWT 校验。

## E2E 验证示例（curl）

### 1. 健康检查（无需 Token）

```bash
curl http://localhost:9202/auth/health
curl http://localhost:9201/platform/health
curl http://localhost:9203/system/health
curl http://localhost:9204/org/health
```

### 2. 登录获取 Token

```bash
curl -X POST http://localhost:9202/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":0,"userName":"admin","password":"admin123"}'
```

响应示例：

```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200
  }
}
```

> **重要**：`tenantId` 字段必传且必须为 `0`（详见下文 P1 已知问题）。

### 3. 带 Token 的业务调用

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# 租户列表
curl http://localhost:9201/platform/tenant/list \
  -H "Authorization: Bearer $TOKEN"

# 字典列表
curl http://localhost:9203/system/dict/list \
  -H "Authorization: Bearer $TOKEN"

# 部门树
curl http://localhost:9204/org/dept/tree \
  -H "Authorization: Bearer $TOKEN"
```

### 4. 无 Token 应返回 401

```bash
curl -i http://localhost:9201/platform/tenant/list
# HTTP/1.1 401
```

## 默认账号

| 字段 | 值 |
|---|---|
| 租户 ID | `0`（系统租户） |
| 用户名 | `admin` |
| 密码 | `admin123` |

## P1 验证结果（详见 `logs/p1-e2e/`）

| 检查项 | 结果 |
|---|---|
| 5 个服务均注册到 Nacos `lumen-public` | ✅ |
| `/auth/login` 返回 JWT | ✅（必须 `tenantId=0`） |
| `/platform/tenant/list` | ✅ 200 |
| `/system/dict/list` | ✅ 200 |
| `/org/dept/tree` | ✅ 200 |
| 无 Token 调用 | ✅ 401 |
| **通过 Gateway 路由调用** | ❌ 503 (`503 Unable to find instance`) |

## P1 已知问题（修复后才能正式上线）

### 1. `TenantInterceptor` 强制注入 `tenant_id = 0`

`com.lumen.common.mybatis.interceptor.TenantInterceptor#getTenantId()` 在
`UserContextHolder.getTenantId()` 为 null 时回退到 `SYSTEM_TENANT_ID = 0`，导致所有
未登录 SQL 自动追加 `AND tenant_id = 0`。登录接口在 SQL 执行前也无法注入租户上下文，
因此：

- 默认 admin 必须 `tenant_id = 0`（已手工插入 `user_id=100`）；
- `/auth/user/profile` 等需要从 JWT 解析租户上下文的接口会 401（`UserContextHolder`
  为 null，被拦截器视为未登录租户）；
- **修复方案**：服务侧需要新增 `UserContextResolver` 过滤器从 `X-Tenant-Id` /
  `X-User-Id` 请求头还原 `UserContextHolder`，或在 `TenantInterceptor` 中通过
  ThreadLocal 跳过登录态 SQL。

### 2. Gateway 路由 503

服务均已注册到 Nacos，但 Gateway 通过 `lb://` 负载均衡时报
`503 Unable to find instance for X-service`。已尝试 `-Dspring.cloud.nacos.discovery.namespace`
显式传值、关闭 `loadbalancer.cache.enabled`、调整日志级别，根因未定位。可能的修复方向：

- 显式配置 `spring.cloud.gateway.discovery.locator.lower-case-service-id=true` 已开启，
  需进一步排查 `spring-cloud-starter-loadbalancer` 与 `nacos-discovery` 版本兼容性；
- Gateway 实例本身注册到 Nacos 的 metadata 可能干扰了 `lb://` 解析，需将
  `spring.cloud.nacos.discovery.register-enabled=false` 仅用于 Gateway。

### 3. Spring Security 默认 Basic 认证

`lumen-common-security` 启用了默认 Spring Security，**所有非白名单接口**会先弹
`WWW-Authenticate: Basic realm="SecurityRealm"`。P1 验证时通过 Basic Auth 携带生成
的用户名/密码绕过（`actuator` 自动生成的随机密码），下一步应在
`SecurityConfig` 中明确关闭 HTTP Basic，仅放行白名单。

## 日志位置

启动日志按服务拆分保存至 `logs/p1-e2e/`：

```
logs/p1-e2e/platform.log
logs/p1-e2e/auth.log
logs/p1-e2e/system.log
logs/p1-e2e/org.log
logs/p1-e2e/gateway.log
```