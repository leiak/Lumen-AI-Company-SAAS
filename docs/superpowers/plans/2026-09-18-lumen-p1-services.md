# Lumen 企业管理平台 P1 微服务骨架实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 P0 阶段预留的 5 个 stub 模块（lumen-platform / lumen-auth / lumen-gateway / lumen-system / lumen-org）落地为**最小可运行的 SpringBoot 微服务**，配合 Nacos 注册中心，实现端到端：客户端 → Gateway → Auth 登录 → 调用其他服务。

**Architecture:**
- 5 个独立 SpringBoot 应用，每个都是 Nacos 客户端
- 单 MySQL 8 库（`lumen_db`），Flyway 管理 DDL，MyBatis-Plus 操作数据
- Redis 缓存（session、限流计数）+ JWT 无状态鉴权
- 服务间 OpenFeign 调用；Gateway 通过 Nacos 服务发现动态路由
- 复用 P0 的 6 个 common 库（common-core/web/security/mybatis/redis/log）

**Tech Stack (新增):**
- Spring Cloud Alibaba 2023.0.1.0（Nacos Discovery、OpenFeign）
- Spring Cloud Gateway 4.x + reactive
- MyBatis-Plus 3.5.5（已就绪）
- Flyway 9.22.3（已就绪）
- jjwt 0.12.5（已就绪）

**P0 已完成的依赖（复用即可）:**
- `lumen-common-core` — R、PageResult、ServiceException、CommonConstants
- `lumen-common-web` — TraceIdFilter、GlobalExceptionAdvice
- `lumen-common-security` — JWT、UserContext、UserContextHolder、JwtTokenProvider
- `lumen-common-mybatis` — BaseEntity、TenantInterceptor、FieldFillHandler
- `lumen-common-redis` — RedisTemplate、RedisUtils、RedisLock
- `lumen-common-log` — Logback + traceId MDC

**不在 P1 范围:**
- ❌ 不做 SSO 实际接入（钉钉/企微），只保留接口和路由
- ❌ 不做 MFA 实际验证（TOTP），只保留开关/字段
- ❌ 不做字段加密（AES）的运行时实现，只保留列定义
- ❌ 不做文件存储、消息中心、审批引擎（这些是后续 P2/P3）

---

## 文件结构（P1 完成后）

```
lumen-parent/
├── lumen-platform/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lumen/platform/
│       │   ├── PlatformApplication.java
│       │   ├── controller/TenantController.java
│       │   ├── service/TenantService.java
│       │   ├── mapper/TenantMapper.java
│       │   └── entity/Tenant.java
│       └── resources/
│           ├── application.yml
│           └── bootstrap.yml
├── lumen-auth/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lumen/auth/
│       │   ├── AuthApplication.java
│       │   ├── controller/AuthController.java
│       │   ├── controller/UserController.java
│       │   ├── service/LoginService.java
│       │   ├── service/SessionService.java
│       │   └── ...
│       └── resources/
│           ├── application.yml
│           └── bootstrap.yml
├── lumen-gateway/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lumen/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── filter/JwtAuthFilter.java
│       │   ├── filter/TenantResolveFilter.java
│       │   └── config/GatewayConfig.java
│       └── resources/application.yml
├── lumen-system/
│   ├── pom.xml
│   └── src/main/...
├── lumen-org/
│   ├── pom.xml
│   └── src/main/...
└── sql/
    ├── V1.0.0__init_tenant_tables.sql          [已存在]
    ├── V1.0.1__init_tenant_seed.sql            [已存在]
    ├── V1.1.0__extend_sys_user_for_multi_tenant.sql
    ├── V1.1.1__extend_sys_role_for_data_scope.sql
    ├── V1.1.2__init_sso_tables.sql
    ├── V1.1.3__init_auth_audit.sql
    ├── V1.2.0__extend_sys_dept_for_org_structure.sql
    ├── V1.2.1__init_org_extension_tables.sql
    ├── V1.3.0__init_system_tables.sql           # RuoYi 标准 sys_dict/sys_config 等
    └── V1.3.1__seed_default_admin.sql
```

---

## Task 1: 扩展 SQL — auth + system + org 表

**Files:**
- Create: `sql/V1.1.0__extend_sys_user_for_multi_tenant.sql`
- Create: `sql/V1.1.1__extend_sys_role_for_data_scope.sql`
- Create: `sql/V1.1.2__init_sso_tables.sql`
- Create: `sql/V1.1.3__init_auth_audit.sql`
- Create: `sql/V1.2.0__extend_sys_dept_for_org_structure.sql`
- Create: `sql/V1.2.1__init_org_extension_tables.sql`
- Create: `sql/V1.3.0__init_system_tables.sql`
- Create: `sql/V1.3.1__seed_default_admin.sql`

**目标：** 准备好 4 个服务所需的全部表 + 默认 admin 用户。**lumen-platform 使用 V1.0.0 已有表**（tenant / tenant_package / tenant_config / common_seq），不需要再改。

**子任务：**

- [ ] **Step 1.1: 写 V1.1.0 — sys_user 扩展**

按附录 G.2 的 schema。字段：`tenant_id, data_scope, mfa_secret, mfa_enabled, pwd_expire_at, pwd_history, id_card_enc, mobile_enc, email_enc, bank_card_enc, last_pwd_change, fail_count, lock_until, deleted`。注意 sys_user 表需先 CREATE（因为 RuoYi 默认会创建，但我们的脚本不依赖 RuoYi）—— 用 `CREATE TABLE IF NOT EXISTS sys_user` 完整建表。

- [ ] **Step 1.2: 写 V1.1.1 — sys_role 扩展**

字段：`tenant_id, api_pattern, i18n_key, data_scope`（用 TINYINT 新列替换旧的 char(1) 列）。完整建表。

- [ ] **Step 1.3: 写 V1.1.2 — SSO 表**

`sys_sso_account` + `sys_user_session` + `sys_login_fail`（参考附录 G.2）。完整建表。

- [ ] **Step 1.4: 写 V1.1.3 — auth_audit 表**

`sys_auth_audit` 记录所有登录/操作审计。完整建表。

- [ ] **Step 1.5: 写 V1.2.0 — sys_dept 扩展**

字段：`tenant_id, ancestors, leader_name, phone, email, sort, status, deleted`。完整建表。

- [ ] **Step 1.6: 写 V1.2.1 — org 扩展表**

`sys_post`（岗位）、`sys_employee`（员工档案 — 区别于 sys_user，因为员工可能有多个账号/无账号）。完整建表。

- [ ] **Step 1.7: 写 V1.3.0 — system 表**

`sys_dict_type`, `sys_dict_data`, `sys_config`, `sys_job`, `sys_job_log`, `sys_oper_log`, `sys_logininfor`。完整建表（精简版）。

- [ ] **Step 1.8: 写 V1.3.1 — seed 默认 admin**

```sql
INSERT INTO sys_user (tenant_id, user_name, nick_name, password, status, data_scope)
VALUES (1, 'admin', '超级管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1, 1);
-- 密码: admin123 (BCrypt 加密)
INSERT INTO sys_role (tenant_id, role_name, role_key, role_sort, data_scope) VALUES
(1, '超级管理员', 'super_admin', 1, 1),
(1, '普通用户',   'user',        2, 4);
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_dept (tenant_id, dept_id, parent_id, ancestors, dept_name, sort, status)
VALUES (1, 100, 0, '0', 'Lumen 总部', 1, 1);
UPDATE sys_user SET dept_id = 100 WHERE user_id = 1;
```

- [ ] **Step 1.9: 启动 MySQL 容器、验证 SQL 自动加载**

```bash
cd lumen-parent
docker compose -f docker/docker-compose.yml up -d mysql
sleep 30
docker exec lumen-mysql mysql -uroot -proot123 lumen_db -e "SHOW TABLES;"
```

期望：至少包含 `tenant, tenant_package, tenant_config, common_seq, sys_user, sys_role, sys_user_role, sys_dept, sys_post, sys_employee, sys_sso_account, sys_user_session, sys_login_fail, sys_auth_audit, sys_dict_type, sys_dict_data, sys_config, sys_job, sys_job_log, sys_oper_log, sys_logininfor`。

- [ ] **Step 1.10: 提交**

```bash
git add sql/
git commit -m "feat(p1): add auth/system/org Flyway migrations + default admin seed"
```

---

## Task 2: lumen-platform 骨架

**Files:**
- Modify: `lumen-parent/lumen-platform/pom.xml`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/PlatformApplication.java`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/entity/Tenant.java`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/mapper/TenantMapper.java`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/service/TenantService.java`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/controller/TenantController.java`
- Create: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/controller/HealthController.java`
- Create: `lumen-parent/lumen-platform/src/main/resources/application.yml`
- Create: `lumen-parent/lumen-platform/src/main/resources/bootstrap.yml`

**目标：** 一个能启动、能注册到 Nacos、能查 tenant 列表、能 ping 的 SpringBoot 应用。

- [ ] **Step 2.1: 写 pom.xml**

依赖：
- `spring-boot-starter-web`
- `lumen-common-core`, `lumen-common-web`, `lumen-common-mybatis`, `lumen-common-redis`, `lumen-common-security`, `lumen-common-log`
- `mybatis-plus-spring-boot3-starter`
- `mysql-connector-j`, `druid-spring-boot-3-starter`, `flyway-core`, `flyway-mysql`
- `spring-cloud-starter-alibaba-nacos-discovery`
- `spring-cloud-starter-bootstrap`

- [ ] **Step 2.2: PlatformApplication**

```java
@SpringBootApplication(scanBasePackages = {"com.lumen.platform", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.platform.mapper")
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
```

- [ ] **Step 2.3: Tenant 实体**

继承 `BaseEntity`（lumen-common-mybatis 提供），字段映射 V1.0.0 表结构。

- [ ] **Step 2.4: TenantMapper**

```java
public interface TenantMapper extends BaseMapper<Tenant> {}
```

- [ ] **Step 2.5: TenantService**

提供：`list(Page)`, `create(Tenant)`, `getById`, `switchMode(id, mode)`。`create` 时检查 code 唯一性。

- [ ] **Step 2.6: TenantController**

| 接口 | 方法 | 路径 |
|---|---|---|
| `GET /platform/tenant/list` | `list` | 分页查询 |
| `POST /platform/tenant/create` | `create` | 创建 |
| `GET /platform/tenant/{id}` | `get` | 详情 |
| `POST /platform/tenant/{id}/switch-mode` | `switchMode` | 切换部署模式 |

- [ ] **Step 2.7: HealthController**

```java
@RestController
@RequestMapping("/platform")
public class HealthController {
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("platform-service is UP");
    }
}
```

- [ ] **Step 2.8: bootstrap.yml**

```yaml
spring:
  application:
    name: platform-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: lumen-public
        username: lumen
        password: lumen
```

- [ ] **Step 2.9: application.yml**

```yaml
server:
  port: 9201
spring:
  profiles:
    active: dev
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    url: jdbc:mysql://localhost:3306/lumen_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

并把 SQL 脚本复制到 `lumen-platform/src/main/resources/db/migration/`（Task 1 的所有 V1.0.0 ~ V1.3.1）—— **Flyway 只能由一个服务执行**，所以选 platform 作为 owner（其他服务不启用 Flyway，依赖 platform 启动后的 schema）。

- [ ] **Step 2.10: 验证启动**

```bash
cd lumen-parent
mvn -pl lumen-platform -am clean install -DskipTests
mvn -pl lumen-platform spring-boot:run &
sleep 30
curl http://localhost:9201/platform/health
# 期望: {"code":200,"msg":"OK","data":"platform-service is UP"}
curl http://localhost:9201/platform/tenant/list?pageNum=1&pageSize=10
# 期望: 包含 default 租户
```

- [ ] **Step 2.11: 提交**

```bash
git add lumen-parent/lumen-platform/
git commit -m "feat(platform): minimal SpringBoot skeleton with tenant CRUD + Nacos registration"
```

---

## Task 3: lumen-auth 骨架

**Files:**
- Modify: `lumen-parent/lumen-auth/pom.xml`
- Create: `lumen-parent/lumen-auth/src/main/java/com/lumen/auth/AuthApplication.java`
- Create: `.../entity/SysUser.java`
- Create: `.../entity/SysUserSession.java`
- Create: `.../entity/SysLoginFail.java`
- Create: `.../mapper/SysUserMapper.java`
- Create: `.../mapper/SysUserSessionMapper.java`
- Create: `.../mapper/SysLoginFailMapper.java`
- Create: `.../service/LoginService.java`
- Create: `.../service/SessionService.java`
- Create: `.../controller/AuthController.java`
- Create: `.../controller/UserController.java`
- Create: `lumen-parent/lumen-auth/src/main/resources/bootstrap.yml`
- Create: `lumen-parent/lumen-auth/src/main/resources/application.yml`

**目标：** JWT login / refresh / logout / profile，登录失败计数（写 Redis + 异步落库）。

- [ ] **Step 3.1: pom.xml**

依赖：与 platform 相同，去掉 Flyway，加 `lumen-common-security` 强依赖。

- [ ] **Step 3.2: AuthApplication**

```java
@SpringBootApplication(scanBasePackages = {"com.lumen.auth", "com.lumen.common"})
@EnableDiscoveryClient
@EnableAsync
@MapperScan("com.lumen.auth.mapper")
public class AuthApplication { ... }
```

- [ ] **Step 3.3: SysUser 实体**

字段：`userId, tenantId, userName, nickName, password, mfaEnabled, mfaSecret, failCount, lockUntil, dataScope, status, deleted`。继承 BaseEntity。

- [ ] **Step 3.4: SysUserSession 实体**

字段：`sessionId, userId, tenantId, refreshToken, ip, userAgent, device, loginAt, lastActiveAt, expireAt, status, logoutAt`。

- [ ] **Step 3.5: Mappers**

`SysUserMapper extends BaseMapper<SysUser>` 提供 `findByTenantAndUsername`。`SysUserSessionMapper extends BaseMapper<SysUserSession>` + `findBySessionId`。

- [ ] **Step 3.6: LoginService**

```java
public LoginResult login(LoginRequest req, HttpServletRequest http) {
    // 1. 校验图形验证码（如果有）
    // 2. 根据 tenantId + userName 查 SysUser
    // 3. 检查 lockUntil > now → 抛出"账户已锁定"
    // 4. BCrypt 校验 password
    // 5. 失败: failCount++，>=5 写 lockUntil；写入 sys_login_fail；返回错误
    // 6. 成功: failCount=0；生成 access_token + refresh_token；写入 sys_user_session
    // 7. 写 sys_auth_audit
    return new LoginResult(accessToken, refreshToken, expiresIn);
}
```

使用 `lumen-common-security` 的 `JwtTokenProvider` 生成 token。

- [ ] **Step 3.7: SessionService**

提供：`createSession(userId, tenantId, ip, ua, device) → sessionId`, `refresh(sessionId) → new tokens`, `revoke(sessionId)`, `getUserIdFromSession(sessionId)`。

- [ ] **Step 3.8: AuthController**

| 接口 | 方法 | 路径 |
|---|---|---|
| `POST /auth/login` | `login` | 账号密码登录（body: tenantId, userName, password, captchaId, captchaCode） |
| `POST /auth/refresh` | `refresh` | body: refreshToken |
| `POST /auth/logout` | `logout` | header: Authorization |
| `GET /auth/user/profile` | `profile` | 当前用户（从 UserContext 读） |

- [ ] **Step 3.9: UserController**

| 接口 | 方法 |
|---|---|
| `POST /auth/user/change-password` | 改密（旧密码校验 + 更新 pwd_history） |

- [ ] **Step 3.10: bootstrap + application.yml**

端口 9202，应用名 `auth-service`。datasource、mybatis-plus、nacos 与 platform 同。

- [ ] **Step 3.11: 验证**

```bash
mvn -pl lumen-auth -am install -DskipTests
mvn -pl lumen-auth spring-boot:run &
sleep 30
TOKEN=$(curl -s -X POST http://localhost:9202/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":1,"userName":"admin","password":"admin123"}' | jq -r .data.accessToken)
echo "Token: $TOKEN"
curl -H "Authorization: Bearer $TOKEN" http://localhost:9202/auth/user/profile
```

- [ ] **Step 3.12: 提交**

```bash
git add lumen-parent/lumen-auth/
git commit -m "feat(auth): JWT login/refresh/logout + session table + login fail count"
```

---

## Task 4: lumen-gateway 骨架

**Files:**
- Modify: `lumen-parent/lumen-gateway/pom.xml`
- Create: `lumen-parent/lumen-gateway/src/main/java/com/lumen/gateway/GatewayApplication.java`
- Create: `.../filter/JwtAuthFilter.java`（GatewayFilter，基于 Spring Cloud Gateway 4 reactive）
- Create: `.../filter/TenantResolveFilter.java`
- Create: `.../config/GatewayConfig.java`
- Create: `lumen-parent/lumen-gateway/src/main/resources/application.yml`

**目标：** 网关通过 Nacos 服务发现动态路由 `/platform/**` → platform-service, `/auth/**` → auth-service（白名单，免 JWT），其他路径需要 JWT。

- [ ] **Step 4.1: pom.xml**

依赖：`spring-cloud-starter-gateway`（webflux，不要 spring-web）, `spring-cloud-starter-alibaba-nacos-discovery`, `spring-cloud-starter-bootstrap`, `lumen-common-core`, `lumen-common-security`（仅用 JwtTokenProvider 解析，不需要 UserContextHolder）。**注意：gateway 用 webflux，不要加 spring-boot-starter-web！**

- [ ] **Step 4.2: GatewayApplication**

```java
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication { ... }
```

- [ ] **Step 4.3: JwtAuthFilter**

继承 `AbstractGatewayFilterFactory<JwtAuthFilter.Config>`，顺序：解析 `Authorization: Bearer xxx` → 用 `JwtTokenProvider.parse(token)` → 把 `userId, tenantId, roles` 写入 exchange 的 request header（X-User-Id, X-Tenant-Id, X-Roles），下游服务读取后 set UserContextHolder。**失败 → 401**。

- [ ] **Step 4.4: TenantResolveFilter**

解析 `X-Tenant-Id` header（或 JWT claim），透传；缺省值 `1`。

- [ ] **Step 4.5: GatewayConfig**

注册以上两个 Filter 为全局过滤器（`@Bean` + `GlobalFilter`）。

- [ ] **Step 4.6: application.yml**

```yaml
server:
  port: 9200
spring:
  application:
    name: lumen-gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: lumen-public
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: auth-route
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
        - id: platform-route
          uri: lb://platform-service
          predicates:
            - Path=/platform/**
```

- [ ] **Step 4.7: 验证**

```bash
mvn -pl lumen-gateway -am install -DskipTests
mvn -pl lumen-gateway spring-boot:run &
sleep 30
curl http://localhost:9200/platform/health
# 期望：转发到 platform-service 并返回健康
curl -X POST http://localhost:9200/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":1,"userName":"admin","password":"admin123"}'
# 期望：auth-service 的登录响应
```

- [ ] **Step 4.8: 提交**

```bash
git add lumen-parent/lumen-gateway/
git commit -m "feat(gateway): reactive gateway with Nacos discovery + JWT auth filter + tenant resolve"
```

---

## Task 5: lumen-system 骨架

**Files:**
- Modify: `lumen-parent/lumen-system/pom.xml`
- Create: `lumen-parent/lumen-system/src/main/java/com/lumen/system/SystemApplication.java`
- Create: `.../entity/SysDictType.java`
- Create: `.../entity/SysDictData.java`
- Create: `.../entity/SysConfig.java`
- Create: `.../entity/SysOperLog.java`
- Create: `.../entity/SysLogininfor.java`
- Create: `.../mapper/*` ×5
- Create: `.../service/DictService.java`
- Create: `.../service/ConfigService.java`
- Create: `.../service/OnlineUserService.java`（读 Redis 中存活的 session）
- Create: `.../controller/DictController.java`
- Create: `.../controller/ConfigController.java`
- Create: `.../controller/OperLogController.java`
- Create: `.../controller/LogininforController.java`
- Create: `.../controller/OnlineUserController.java`
- Create: `.../controller/HealthController.java`
- Create: `resources/bootstrap.yml`, `application.yml`

**目标：** 字典/配置/操作日志/登录日志查询接口（只读 + 简单 update），在线用户查询（从 Redis 拿 session 信息）。

- [ ] **Step 5.1: pom.xml**

与 platform/auth 同（无 Flyway）。

- [ ] **Step 5.2: SystemApplication**

```java
@SpringBootApplication(scanBasePackages = {"com.lumen.system", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.system.mapper")
public class SystemApplication { ... }
```

- [ ] **Step 5.3: 5 个实体 + Mapper**

字段对应 V1.3.0 表结构。

- [ ] **Step 5.4: 4 个 Service**

- `DictService.list(type, page)`, `getValue(type, key)`, `create/update/delete`
- `ConfigService.list(page)`, `getByKey`, `update`（更新后写 Redis 失效）
- `OperLogService.list(filter, page)`
- `LogininforService.list(filter, page)`
- `OnlineUserService.list(page)` —— 查 Redis key `auth:session:*`（需 auth 服务写入；这里只是查询）

- [ ] **Step 5.5: 5 个 Controller**

| 接口 | 路径 |
|---|---|
| 字典列表 | `GET /system/dict/list` |
| 字典值 | `GET /system/dict/{type}` |
| 配置 | `GET /system/config/{key}`, `PUT /system/config/{key}` |
| 操作日志 | `GET /system/oper-log/list` |
| 登录日志 | `GET /system/logininfor/list` |
| 在线用户 | `GET /system/online/list` |
| 健康 | `GET /system/health` |

- [ ] **Step 5.6: bootstrap + application.yml**

端口 9203，应用名 `system-service`。

- [ ] **Step 5.7: 验证**

```bash
mvn -pl lumen-system -am install -DskipTests
mvn -pl lumen-system spring-boot:run &
sleep 25
curl http://localhost:9203/system/health
curl http://localhost:9203/system/dict/list
```

- [ ] **Step 5.8: 提交**

```bash
git add lumen-parent/lumen-system/
git commit -m "feat(system): dict/config/oper-log/logininfor/online-user CRUD + health"
```

---

## Task 6: lumen-org 骨架

**Files:**
- Modify: `lumen-parent/lumen-org/pom.xml`
- Create: `lumen-parent/lumen-org/src/main/java/com/lumen/org/OrgApplication.java`
- Create: `.../entity/SysDept.java`
- Create: `.../entity/SysPost.java`
- Create: `.../entity/SysEmployee.java`
- Create: `.../mapper/*` ×3
- Create: `.../service/DeptService.java`
- Create: `.../service/PostService.java`
- Create: `.../service/EmployeeService.java`
- Create: `.../controller/DeptController.java`
- Create: `.../controller/PostController.java`
- Create: `.../controller/EmployeeController.java`
- Create: `.../controller/HealthController.java`
- Create: `resources/bootstrap.yml`, `application.yml`

**目标：** 部门/岗位/员工基础 CRUD + 组织架构树查询。

- [ ] **Step 6.1: pom.xml**

与 platform/auth/system 同。

- [ ] **Step 6.2: OrgApplication**

```java
@SpringBootApplication(scanBasePackages = {"com.lumen.org", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.org.mapper")
public class OrgApplication { ... }
```

- [ ] **Step 6.3: 3 个实体 + Mapper**

- `SysDept`: deptId, parentId, ancestors, deptName, leaderName, phone, email, sort, status, deleted, tenantId
- `SysPost`: postId, tenantId, postCode, postName, postSort, status, deleted
- `SysEmployee`: employeeId, tenantId, userId (nullable), employeeNo, name, mobileEnc, emailEnc, idCardEnc, deptId, postId, hireDate, status, deleted

- [ ] **Step 6.4: DeptService**

`tree()` 返回嵌套结构（递归查 SysDept，按 ancestors 排序）。`create/update/move/delete`（move 需要更新 ancestors）。

- [ ] **Step 6.5: PostService**

`list(page)`, `create/update/delete`。

- [ ] **Step 6.6: EmployeeService**

`list(filter, page)`, `create/update/delete`, `getByUserId(userId)`。

- [ ] **Step 6.7: 4 个 Controller**

| 接口 | 路径 |
|---|---|
| 部门树 | `GET /org/dept/tree` |
| 部门 CRUD | `/org/dept/list`, `POST /org/dept`, `PUT /org/dept/{id}`, `DELETE /org/dept/{id}` |
| 岗位 CRUD | `/org/post/**` |
| 员工 CRUD | `/org/employee/**` |
| 健康 | `GET /org/health` |

- [ ] **Step 6.8: bootstrap + application.yml**

端口 9204，应用名 `org-service`。

- [ ] **Step 6.9: 验证**

```bash
mvn -pl lumen-org -am install -DskipTests
mvn -pl lumen-org spring-boot:run &
sleep 25
curl http://localhost:9204/org/health
curl http://localhost:9204/org/dept/tree
```

- [ ] **Step 6.10: 提交**

```bash
git add lumen-parent/lumen-org/
git commit -m "feat(org): dept tree + post/employee CRUD + health"
```

---

## Task 7: 端到端集成验证

**目标：** 通过 Gateway 走完整链路：登录 → 拿 JWT → 用 JWT 调其他服务。

- [ ] **Step 7.1: 全栈启动**

```bash
cd lumen-parent
docker compose -f docker/docker-compose.yml up -d  # 启 mysql/redis/nacos
mvn clean install -DskipTests                       # 全量构建
# 各服务后台启动（用 nohup 或 &）
nohup mvn -pl lumen-platform spring-boot:run > /tmp/platform.log 2>&1 &
nohup mvn -pl lumen-auth spring-boot:run > /tmp/auth.log 2>&1 &
nohup mvn -pl lumen-gateway spring-boot:run > /tmp/gateway.log 2>&1 &
nohup mvn -pl lumen-system spring-boot:run > /tmp/system.log 2>&1 &
nohup mvn -pl lumen-org spring-boot:run > /tmp/org.log 2>&1 &
sleep 60
```

- [ ] **Step 7.2: E2E 测试**

```bash
# 1. 通过网关登录
LOGIN=$(curl -s -X POST http://localhost:9200/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":1,"userName":"admin","password":"admin123"}')
TOKEN=$(echo $LOGIN | jq -r .data.accessToken)
echo "Token acquired: ${TOKEN:0:30}..."

# 2. 通过网关调各服务
curl -H "Authorization: Bearer $TOKEN" http://localhost:9200/auth/user/profile
curl -H "Authorization: Bearer $TOKEN" http://localhost:9200/platform/tenant/list
curl -H "Authorization: Bearer $TOKEN" http://localhost:9200/system/dict/list
curl -H "Authorization: Bearer $TOKEN" http://localhost:9200/org/dept/tree

# 3. 健康检查（不需要 token）
curl http://localhost:9200/platform/health
curl http://localhost:9200/system/health
curl http://localhost:9200/org/health

# 4. Nacos 注册确认
curl -u lumen:lumen "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10" \
  | jq '.doms[]' | sort -u
# 期望看到：["auth-service","lumen-gateway","org-service","platform-service","system-service"]
```

- [ ] **Step 7.3: 验证 Nacos namespace 隔离**

如果 lumen-public namespace 下能看到 5 个服务 = 通过。

- [ ] **Step 7.4: 写 README + 提交**

更新 `lumen-parent/README.md`：5 个服务的端口、启动顺序、E2E 测试命令。

```bash
git add lumen-parent/README.md
git commit -m "docs(p1): add service startup guide + E2E test script"
```

---

## 验收标准（P1 完成定义）

- [ ] `docker compose up -d` 启动 mysql/redis/nacos
- [ ] `mvn clean install -DskipTests` 编译 11 个模块全部通过
- [ ] 5 个 SpringBoot 服务都能启动并注册到 Nacos 的 `lumen-public` namespace
- [ ] Gateway 9200 端口能正确路由 `/auth/**`、`/platform/**`、`/system/**`、`/org/**`
- [ ] 通过 `/auth/login` 拿到 admin token 后，可调用其他服务的受保护接口
- [ ] Flyway 脚本由 platform 服务启动时执行，其他服务不重复执行
- [ ] 所有 commit 推送，`main` 分支干净

---

## 后续 P2/P3 预告（不在 P1 范围）

- **P2:** SSO 实际接入、MFA TOTP、字段加密运行时、Sentinel 限流、灰度路由
- **P3:** 17 个业务模块的 spec → plan → 实现
