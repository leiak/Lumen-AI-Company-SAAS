# Lumen 企业管理平台 P0 基础阶段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建企业管理系统最小化可运行的 P0 基础平台，包含统一网关、认证授权、用户/角色/部门管理、租户管理、组织架构，让后续 P1-P4 业务模块可直接挂接。

**Architecture:** Spring Cloud Alibaba 微服务架构，单 MySQL 8 库（业务前缀），Nacos 配置/注册中心，Gateway + Sentinel 网关，Spring Security 6 + JWT 鉴权，多租户（行级隔离），Flyway 管理 DDL，MyBatis-Plus 操作数据库，Redis 缓存 + 分布式锁，Caffeine 本地缓存，SkyWalking 链路追踪。

**Tech Stack:**
- 后端：Spring Boot 3.2 + Spring Cloud 2023 + Spring Cloud Alibaba 2023
- 注册/配置：Nacos 2.3
- 网关：Spring Cloud Gateway 4.x + Sentinel
- 持久层：MyBatis-Plus 3.5 + MySQL 8 + Druid + Flyway 9
- 缓存：Redis 7 + Caffeine
- 安全：Spring Security 6 + JWT (jjwt 0.12) + jasypt 加密
- 链路追踪：SkyWalking 9 + Micrometer
- 测试：JUnit 5 + Mockito + Testcontainers
- 构建：Maven 3.9

---

## 文件结构

```
lumen-parent/
├── pom.xml                          # 父 POM
├── lumen-common/
│   ├── lumen-common-core/           # 通用工具
│   ├── lumen-common-redis/          # Redis 封装
│   ├── lumen-common-security/       # 安全/JWT
│   ├── lumen-common-web/            # Web 通用
│   ├── lumen-common-mybatis/        # MyBatis 拦截器
│   └── lumen-common-log/            # 日志
├── lumen-gateway/                   # 统一网关
├── lumen-auth/                      # 认证服务
├── lumen-system/                    # 系统服务
├── lumen-platform/                  # 平台服务
├── lumen-org/                       # 组织服务
├── sql/                             # 初始化 SQL
├── docker/                          # Docker Compose
└── docs/
    └── api/                         # API 文档
```

---

## Task 1: 初始化父 POM 工程

**Files:**
- Create: `lumen-parent/pom.xml`
- Create: `lumen-parent/.gitignore`
- Create: `lumen-parent/README.md`

- [ ] **Step 1: 创建父 POM**

文件 `lumen-parent/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.lumen</groupId>
    <artifactId>lumen-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>lumen-parent</name>
    <description>Lumen 企业管理平台 - 父工程</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

        <spring-boot.version>3.2.5</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <mysql.version>8.0.33</mysql.version>
        <druid.version>1.2.22</druid.version>
        <flyway.version>9.22.3</flyway.version>
        <jjwt.version>0.12.5</jjwt.version>
        <hutool.version>5.8.27</hutool.version>
        <lombok.version>1.18.30</lombok.version>
        <skywalking.version>9.5.0</skywalking.version>
        <testcontainers.version>1.19.7</testcontainers.version>
    </properties>

    <modules>
        <module>lumen-common/lumen-common-core</module>
        <module>lumen-common/lumen-common-redis</module>
        <module>lumen-common/lumen-common-security</module>
        <module>lumen-common/lumen-common-web</module>
        <module>lumen-common/lumen-common-mybatis</module>
        <module>lumen-common/lumen-common-log</module>
        <module>lumen-gateway</module>
        <module>lumen-auth</module>
        <module>lumen-system</module>
        <module>lumen-platform</module>
        <module>lumen-org</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud Alibaba BOM -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- 内部通用模块 -->
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-redis</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-security</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-web</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-mybatis</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.lumen</groupId>
                <artifactId>lumen-common-log</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- MyBatis-Plus -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <!-- MySQL 驱动 -->
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>${mysql.version}</version>
            </dependency>
            <!-- Druid -->
            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-3-starter</artifactId>
                <version>${druid.version}</version>
            </dependency>
            <!-- Flyway -->
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-core</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-mysql</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <!-- JJWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <!-- Hutool -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>
            <!-- Testcontainers -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 创建 .gitignore**

文件 `lumen-parent/.gitignore`：

```
HELP.md
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

### IntelliJ IDEA ###
.idea/
*.iws
*.iml
*.ipr

### Eclipse ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/
build/
!**/src/main/**/build/
!**/src/test/**/build/

### VS Code ###
.vscode/

### Mac OS ###
.DS_Store

### Logs ###
*.log
logs/
```

- [ ] **Step 3: 创建 README**

文件 `lumen-parent/README.md`：

```markdown
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
```

- [ ] **Step 4: 验证 Maven 编译**

Run:
```bash
cd lumen-parent && mvn -N validate
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
cd lumen-parent
git init
git add .
git commit -m "feat(p0): init parent pom and project structure"
```

---

## Task 2: 创建 lumen-common-core 通用工具模块

**Files:**
- Create: `lumen-common/lumen-common-core/pom.xml`
- Create: `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/constant/CommonConstants.java`
- Create: `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/domain/R.java`
- Create: `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/domain/PageResult.java`
- Create: `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/exception/ServiceException.java`
- Create: `lumen-common/lumen-common-core/src/test/java/com/lumen/common/core/RTest.java`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-core/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-core</artifactId>
    <name>lumen-common-core</name>
    <description>通用工具模块</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 CommonConstants**

文件 `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/constant/CommonConstants.java`：

```java
package com.lumen.common.core.constant;

/**
 * 通用常量
 */
public interface CommonConstants {

    /** 成功 */
    int SUCCESS_CODE = 200;

    /** 失败 */
    int FAIL_CODE = 500;

    /** 未授权 */
    int UNAUTHORIZED = 401;

    /** 无权限 */
    int FORBIDDEN = 403;

    /** 资源不存在 */
    int NOT_FOUND = 404;

    /** 默认租户ID */
    Long DEFAULT_TENANT_ID = 1L;

    /** 默认租户编码 */
    String DEFAULT_TENANT_CODE = "default";

    /** Header - 租户ID */
    String HEADER_TENANT_ID = "X-Tenant-Id";

    /** Header - TraceID */
    String HEADER_TRACE_ID = "X-Trace-Id";

    /** Header - Authorization */
    String HEADER_AUTHORIZATION = "Authorization";

    /** Token 前缀 */
    String TOKEN_PREFIX = "Bearer ";

    /** 逻辑删除 - 未删除 */
    int NOT_DELETED = 0;

    /** 逻辑删除 - 已删除 */
    int DELETED = 1;
}
```

- [ ] **Step 3: 创建统一响应 R**

文件 `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/domain/R.java`：

```java
package com.lumen.common.core.domain;

import com.lumen.common.core.constant.CommonConstants;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应
 */
@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(CommonConstants.SUCCESS_CODE, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CommonConstants.SUCCESS_CODE, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(CommonConstants.SUCCESS_CODE, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(CommonConstants.FAIL_CODE, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        return new R<>(code, msg, data);
    }
}
```

- [ ] **Step 4: 创建分页结果 PageResult**

文件 `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/domain/PageResult.java`：

```java
package com.lumen.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public PageResult(long total, long pageNum, long pageSize, List<T> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }

    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> records) {
        return new PageResult<>(total, pageNum, pageSize, records);
    }
}
```

- [ ] **Step 5: 创建业务异常 ServiceException**

文件 `lumen-common/lumen-common-core/src/main/java/com/lumen/common/core/exception/ServiceException.java`：

```java
package com.lumen.common.core.exception;

import lombok.Getter;

import java.io.Serializable;

/**
 * 业务异常
 */
@Getter
public class ServiceException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
```

- [ ] **Step 6: 编写 R 单元测试**

文件 `lumen-common/lumen-common-core/src/test/java/com/lumen/common/core/RTest.java`：

```java
package com.lumen.common.core;

import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RTest {

    @Test
    void ok_shouldReturnSuccessCode() {
        R<String> r = R.ok("hello");
        assertEquals(CommonConstants.SUCCESS_CODE, r.getCode());
        assertEquals("hello", r.getData());
        assertEquals("操作成功", r.getMsg());
    }

    @Test
    void ok_withoutData_shouldReturnNullData() {
        R<Void> r = R.ok();
        assertEquals(CommonConstants.SUCCESS_CODE, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void fail_shouldReturnFailCode() {
        R<Void> r = R.fail("出错了");
        assertEquals(CommonConstants.FAIL_CODE, r.getCode());
        assertEquals("出错了", r.getMsg());
    }

    @Test
    void fail_withCode_shouldUseCustomCode() {
        R<Void> r = R.fail(403, "无权限");
        assertEquals(403, r.getCode());
        assertEquals("无权限", r.getMsg());
    }
}
```

- [ ] **Step 7: 运行测试**

Run:
```bash
mvn -pl lumen-common/lumen-common-core test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8: 提交**

```bash
git add lumen-common/lumen-common-core
git commit -m "feat(common-core): add R, PageResult, ServiceException"
```

---

## Task 3: 创建 lumen-common-web Web 通用模块

**Files:**
- Create: `lumen-common/lumen-common-web/pom.xml`
- Create: `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/advice/GlobalExceptionAdvice.java`
- Create: `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/advice/ResultResponseAdvice.java`
- Create: `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/filter/TraceIdFilter.java`
- Create: `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/config/WebAutoConfig.java`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-web/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-web</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 TraceId 过滤器**

文件 `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/filter/TraceIdFilter.java`：

```java
package com.lumen.common.web.filter;

import com.lumen.common.core.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceID 过滤器
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(CommonConstants.HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("traceId", traceId);
        response.setHeader(CommonConstants.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

- [ ] **Step 3: 创建全局异常处理**

文件 `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/advice/GlobalExceptionAdvice.java`：

```java
package com.lumen.common.web.advice;

import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletRequest req) {
        log.warn("业务异常 [{}]: {}", req.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return R.fail(CommonConstants.FAIL_CODE, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return R.fail(CommonConstants.FAIL_CODE, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArg(IllegalArgumentException e) {
        return R.fail(CommonConstants.FAIL_CODE, "参数错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAll(Exception e, HttpServletRequest req) {
        log.error("系统异常 [{}]", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(R.fail(CommonConstants.FAIL_CODE, "系统繁忙，请稍后再试"));
    }
}
```

- [ ] **Step 4: 创建自动配置类**

文件 `lumen-common/lumen-common-web/src/main/java/com/lumen/common/web/config/WebAutoConfig.java`：

```java
package com.lumen.common.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Web 通用自动配置
 */
@Configuration
@ComponentScan("com.lumen.common.web")
public class WebAutoConfig {
}
```

- [ ] **Step 5: 创建 spring.factories**

文件 `lumen-common/lumen-common-web/src/main/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.lumen.common.web.config.WebAutoConfig
```

- [ ] **Step 6: 编译验证**

Run:
```bash
mvn -pl lumen-common/lumen-common-web clean install -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: 提交**

```bash
git add lumen-common/lumen-common-web
git commit -m "feat(common-web): add global exception handler and trace filter"
```

---

## Task 4: 创建 lumen-common-redis Redis 封装

**Files:**
- Create: `lumen-common/lumen-common-redis/pom.xml`
- Create: `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/config/RedisAutoConfig.java`
- Create: `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/utils/RedisUtils.java`
- Create: `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/lock/RedisLock.java`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-redis/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-redis</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 Redis 配置**

文件 `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/config/RedisAutoConfig.java`：

```java
package com.lumen.common.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 自动配置
 */
@AutoConfiguration
public class RedisAutoConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(om.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL);

        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(om, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

- [ ] **Step 3: 创建 RedisUtils**

文件 `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/utils/RedisUtils.java`：

```java
package com.lumen.common.redis.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void setSeconds(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long seconds) {
        return redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }
}
```

- [ ] **Step 4: 创建分布式锁**

文件 `lumen-common/lumen-common-redis/src/main/java/com/lumen/common/redis/lock/RedisLock.java`：

```java
package com.lumen.common.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式锁 (基于 SET NX + Lua)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String UNLOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    /**
     * 尝试加锁并执行
     */
    public <T> T tryLock(String key, Duration timeout, Supplier<T> action) {
        String lockKey = "lumen:lock:" + key;
        String lockValue = UUID.randomUUID().toString();
        boolean ok = Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeout));
        if (!ok) {
            throw new RuntimeException("获取锁失败: " + key);
        }
        try {
            return action.get();
        } finally {
            // Lua 脚本保证只删自己的锁
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
        }
    }
}
```

- [ ] **Step 5: 创建 spring 工厂文件**

文件 `lumen-common/lumen-common-redis/src/main/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.lumen.common.redis.config.RedisAutoConfig
```

- [ ] **Step 6: 编译验证**

Run:
```bash
mvn -pl lumen-common/lumen-common-redis clean install -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: 提交**

```bash
git add lumen-common/lumen-common-redis
git commit -m "feat(common-redis): add RedisTemplate, RedisUtils, RedisLock"
```

---

## Task 5: 创建 lumen-common-security 安全模块

**Files:**
- Create: `lumen-common/lumen-common-security/pom.xml`
- Create: `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/context/UserContext.java`
- Create: `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/context/UserContextHolder.java`
- Create: `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/jwt/JwtProperties.java`
- Create: `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/jwt/JwtTokenProvider.java`
- Create: `lumen-common/lumen-common-security/src/test/java/com/lumen/common/security/jwt/JwtTokenProviderTest.java`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-security/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-security</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 UserContext**

文件 `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/context/UserContext.java`：

```java
package com.lumen.common.security.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 当前登录用户上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String userName;
    private String nickName;
    private Long tenantId;
    private String tenantCode;
    private Long deptId;
    private Set<String> roles;
    private Set<String> permissions;
    private Integer dataScope;
    private String tokenId;
}
```

- [ ] **Step 3: 创建 UserContextHolder**

文件 `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/context/UserContextHolder.java`：

```java
package com.lumen.common.security.context;

/**
 * 用户上下文 (基于 ThreadLocal)
 */
public class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        CONTEXT.set(ctx);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static Long getTenantId() {
        UserContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.getTenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

- [ ] **Step 4: 创建 JWT 配置类**

文件 `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/jwt/JwtProperties.java`：

```java
package com.lumen.common.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lumen.security.jwt")
public class JwtProperties {
    /** 密钥（至少 32 字符） */
    private String secret = "lumen-default-secret-key-please-change-in-production-env-32bytes";
    /** Access Token 有效期（秒），默认 30 分钟 */
    private long accessExpireSeconds = 1800;
    /** Refresh Token 有效期（秒），默认 7 天 */
    private long refreshExpireSeconds = 604800;
    /** Token 签发者 */
    private String issuer = "lumen";
    /** Token 请求头 */
    private String header = "Authorization";
    /** Token 前缀 */
    private String tokenPrefix = "Bearer ";
}
```

- [ ] **Step 5: 创建 JWT Token 提供者**

文件 `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/jwt/JwtTokenProvider.java`：

```java
package com.lumen.common.security.jwt;

import com.lumen.common.security.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Token 提供者
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final SecretKey signingKey;

    @Autowired
    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.signingKey = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserContext ctx) {
        return generateToken(ctx, "access", props.getAccessExpireSeconds());
    }

    public String generateRefreshToken(UserContext ctx) {
        return generateToken(ctx, "refresh", props.getRefreshExpireSeconds());
    }

    private String generateToken(UserContext ctx, String type, long expireSeconds) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", type);
        claims.put("uid", ctx.getUserId());
        claims.put("tid", ctx.getTenantId());
        claims.put("uname", ctx.getUserName());
        claims.put("nname", ctx.getNickName());
        claims.put("did", ctx.getDeptId());
        claims.put("ds", ctx.getDataScope());

        return Jwts.builder()
            .id(jti)
            .subject(String.valueOf(ctx.getUserId()))
            .issuer(props.getIssuer())
            .issuedAt(now)
            .expiration(exp)
            .claims(claims)
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            throw e;
        }
    }

    public UserContext extractUserContext(String token) {
        Claims c = parseToken(token);
        UserContext ctx = new UserContext();
        ctx.setUserId(c.get("uid", Long.class));
        ctx.setTenantId(c.get("tid", Long.class));
        ctx.setUserName(c.get("uname", String.class));
        ctx.setNickName(c.get("nname", String.class));
        ctx.setDeptId(c.get("did", Long.class));
        ctx.setDataScope(c.get("ds", Integer.class));
        ctx.setTokenId(c.getId());
        return ctx;
    }

    public boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
}
```

- [ ] **Step 6: 编写 JWT 单元测试**

文件 `lumen-common/lumen-common-security/src/test/java/com/lumen/common/security/jwt/JwtTokenProviderTest.java`：

```java
package com.lumen.common.security.jwt;

import com.lumen.common.security.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("test-secret-key-32-bytes-min-length-1234567890");
        provider = new JwtTokenProvider(props);
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        UserContext ctx = UserContext.builder()
            .userId(100L)
            .tenantId(1L)
            .userName("admin")
            .nickName("管理员")
            .deptId(10L)
            .dataScope(1)
            .build();

        String token = provider.generateAccessToken(ctx);
        assertNotNull(token);

        Claims claims = provider.parseToken(token);
        assertEquals("100", claims.getSubject());
        assertEquals(100L, claims.get("uid", Long.class));
        assertEquals(1L, claims.get("tid", Long.class));
        assertEquals("admin", claims.get("uname", String.class));
    }

    @Test
    void shouldExtractUserContext() {
        UserContext ctx = UserContext.builder()
            .userId(200L)
            .tenantId(2L)
            .userName("user1")
            .build();

        String token = provider.generateAccessToken(ctx);
        UserContext extracted = provider.extractUserContext(token);

        assertEquals(200L, extracted.getUserId());
        assertEquals(2L, extracted.getTenantId());
        assertEquals("user1", extracted.getUserName());
        assertNotNull(extracted.getTokenId());
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThrows(JwtException.class, () -> provider.parseToken("invalid.token.here"));
    }

    @Test
    void shouldDetectExpired() {
        props.setAccessExpireSeconds(-1);  // 已过期
        UserContext ctx = UserContext.builder().userId(1L).tenantId(1L).build();
        String token = provider.generateAccessToken(ctx);
        assertTrue(provider.isExpired(token));
    }
}
```

- [ ] **Step 7: 创建 spring factories**

文件 `lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/config/SecurityAutoConfig.java`：

```java
package com.lumen.common.security.config;

import com.lumen.common.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.lumen.common.security")
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfig {
}
```

文件 `lumen-common/lumen-common-security/src/main/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.lumen.common.security.config.SecurityAutoConfig
```

- [ ] **Step 8: 运行测试**

Run:
```bash
mvn -pl lumen-common/lumen-common-security test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: 提交**

```bash
git add lumen-common/lumen-common-security
git commit -m "feat(common-security): add JWT token provider and user context"
```

---

## Task 6: 创建 lumen-common-mybatis 拦截器

**Files:**
- Create: `lumen-common/lumen-common-mybatis/pom.xml`
- Create: `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/config/MybatisAutoConfig.java`
- Create: `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/interceptor/TenantInterceptor.java`
- Create: `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/interceptor/FieldFillHandler.java`
- Create: `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/entity/BaseEntity.java`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-mybatis/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-mybatis</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 BaseEntity**

文件 `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/entity/BaseEntity.java`：

```java
package com.lumen.common.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
```

- [ ] **Step 3: 创建字段自动填充**

文件 `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/interceptor/FieldFillHandler.java`：

```java
package com.lumen.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lumen.common.security.context.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充
 */
@Component
public class FieldFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long uid = UserContextHolder.getUserId();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createBy", Long.class, uid == null ? 0L : uid);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateBy", Long.class, uid == null ? 0L : uid);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long uid = UserContextHolder.getUserId();
        strictUpdateFill(metaObject, "updateBy", Long.class, uid == null ? 0L : uid);
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 4: 创建多租户拦截器**

文件 `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/interceptor/TenantInterceptor.java`：

```java
package com.lumen.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.plugins.interceptor.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.lumen.common.security.context.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 多租户拦截器
 */
@Component
public class TenantInterceptor implements TenantLineHandler {

    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
        "sys_config", "sys_dict_type", "sys_dict_data",
        "tenant", "tenant_package", "lumen_application",
        "common_seq"
    ));

    @Override
    public Expression getTenantId() {
        Long tid = UserContextHolder.getTenantId();
        return tid == null ? new NullValue() : new LongValue(tid);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName.toLowerCase())
            || InterceptorIgnoreHelper.willIgnoreTenantLine("tenantLine");
    }
}
```

- [ ] **Step 5: 创建 MyBatis 自动配置**

文件 `lumen-common/lumen-common-mybatis/src/main/java/com/lumen/common/mybatis/config/MybatisAutoConfig.java`：

```java
package com.lumen.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.lumen.common.mybatis.interceptor.FieldFillHandler;
import com.lumen.common.mybatis.interceptor.TenantInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Autowired TenantInterceptor tenantInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantInterceptor));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldFillHandler fieldFillHandler() {
        return new FieldFillHandler();
    }
}
```

- [ ] **Step 6: 创建 spring factories**

文件 `lumen-common/lumen-common-mybatis/src/main/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.lumen.common.mybatis.config.MybatisAutoConfig
```

- [ ] **Step 7: 编译验证**

Run:
```bash
mvn -pl lumen-common/lumen-common-mybatis clean install -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: 提交**

```bash
git add lumen-common/lumen-common-mybatis
git commit -m "feat(common-mybatis): add multi-tenant and field-fill interceptors"
```

---

## Task 7: 创建 lumen-common-log 日志模块

**Files:**
- Create: `lumen-common/lumen-common-log/pom.xml`
- Create: `lumen-common/lumen-common-log/src/main/resources/logback-spring.xml`

- [ ] **Step 1: 创建 POM**

文件 `lumen-common/lumen-common-log/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lumen</groupId>
        <artifactId>lumen-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>lumen-common-log</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.lumen</groupId>
            <artifactId>lumen-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 logback 配置**

文件 `lumen-common/lumen-common-log/src/main/resources/logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">
    <property name="LOG_PATH" value="${LOG_PATH:-./logs}"/>
    <property name="APP_NAME" value="${APP_NAME:-lumen}"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [traceId=%X{traceId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE_INFO" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}/info.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}/info.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>30GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [traceId=%X{traceId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>INFO</level>
        </filter>
    </appender>

    <appender name="FILE_ERROR" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}/error.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}/error.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [traceId=%X{traceId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
    </appender>

    <logger name="com.lumen" level="DEBUG"/>
    <logger name="com.alibaba.nacos" level="WARN"/>
    <logger name="org.springframework" level="INFO"/>
    <logger name="com.baomidou.mybatisplus" level="WARN"/>
    <logger name="com.zaxxer.hikari" level="WARN"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE_INFO"/>
        <appender-ref ref="FILE_ERROR"/>
    </root>
</configuration>
```

- [ ] **Step 3: 提交**

```bash
git add lumen-common/lumen-common-log
git commit -m "feat(common-log): add logback configuration with traceId"
```

---

## Task 8: 准备数据库（Docker + 初始化 SQL）

**Files:**
- Create: `lumen-parent/docker/docker-compose.yml`
- Create: `lumen-parent/sql/V1.0.0__init_tenant_tables.sql`
- Create: `lumen-parent/sql/V1.0.1__init_tenant_seed.sql`

- [ ] **Step 1: 启动 Docker 基础设施**

文件 `lumen-parent/docker/docker-compose.yml`：

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: lumen-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: lumen_db
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - ./mysql-data:/var/lib/mysql
      - ../sql:/docker-entrypoint-initdb.d
    command:
      --character-set-server=utf8mb4
      --collation-server=utf8mb4_unicode_ci
      --default-time-zone=+08:00
      --max_connections=500
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot123"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: lumen-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - ./redis-data:/data
    command: redis-server --appendonly yes --requirepass redis123
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "redis123", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  nacos:
    image: nacos/nacos-server:v2.3.2
    container_name: lumen-nacos
    restart: always
    environment:
      MODE: standalone
      JVM_XMS: 512m
      JVM_XMX: 512m
      JVM_XMN: 256m
      SPRING_DATASOURCE_PLATFORM: mysql
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_TOKEN: SecretKey012345678901234567890123456789012345678901234567890123456789
      NACOS_AUTH_IDENTITY_KEY: lumen
      NACOS_AUTH_IDENTITY_VALUE: lumen
    ports:
      - "8848:8848"
      - "9848:9848"
```

- [ ] **Step 2: 启动服务**

Run:
```bash
cd lumen-parent/docker && docker compose up -d
```

Expected: 看到 `mysql`, `redis`, `nacos` 三个容器 running

- [ ] **Step 3: 验证 MySQL**

Run:
```bash
docker exec lumen-mysql mysql -uroot -proot123 -e "SHOW DATABASES;" 2>&1 | grep lumen_db
```

Expected: `lumen_db`

- [ ] **Step 4: 创建租户基础表 SQL**

文件 `lumen-parent/sql/V1.0.0__init_tenant_tables.sql`：

```sql
-- ============================================================
-- 租户主表
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    short_name      VARCHAR(50)     DEFAULT '',
    contact_name    VARCHAR(50)     DEFAULT '',
    contact_phone   VARCHAR(50)     DEFAULT '',
    contact_email   VARCHAR(100)    DEFAULT '',
    industry        VARCHAR(50)     DEFAULT '',
    scale           VARCHAR(20)     DEFAULT '',
    region          VARCHAR(50)     DEFAULT '',
    package_id      BIGINT          NOT NULL DEFAULT 1,
    status          TINYINT         NOT NULL DEFAULT 1,
    trial_days      INT             NOT NULL DEFAULT 30,
    expire_at       DATETIME        DEFAULT NULL,
    activated_at    DATETIME        DEFAULT NULL,
    logo_url        VARCHAR(500)    DEFAULT NULL,
    description     VARCHAR(500)    DEFAULT NULL,
    create_by       BIGINT          NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          NOT NULL DEFAULT 0,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (code, deleted),
    KEY idx_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户主表';

CREATE TABLE IF NOT EXISTS tenant_package (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    modules         JSON            NOT NULL,
    max_users       INT             NOT NULL DEFAULT 10,
    max_storage_gb  INT             NOT NULL DEFAULT 5,
    max_employees   INT             NOT NULL DEFAULT 100,
    price_cents     BIGINT          NOT NULL DEFAULT 0,
    duration_days   INT             NOT NULL DEFAULT 365,
    description     VARCHAR(500)    DEFAULT NULL,
    is_builtin      TINYINT         NOT NULL DEFAULT 0,
    create_by, create_time, update_by, update_time, deleted,
    PRIMARY KEY (id),
    UNIQUE KEY uk_package_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT          NOT NULL,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    TEXT            DEFAULT NULL,
    value_type      VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    create_by, create_time, update_by, update_time,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS common_seq (
    seq_name        VARCHAR(50)     NOT NULL,
    current_val     BIGINT          NOT NULL DEFAULT 1,
    step            INT             NOT NULL DEFAULT 1,
    prefix          VARCHAR(20)     DEFAULT '',
    format          VARCHAR(50)     DEFAULT '{prefix}{yyyyMMdd}{seq:6}',
    description     VARCHAR(200)    DEFAULT NULL,
    create_by, create_time, update_by, update_time,
    PRIMARY KEY (seq_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 5: 创建种子数据 SQL**

文件 `lumen-parent/sql/V1.0.1__init_tenant_seed.sql`：

```sql
INSERT INTO tenant (id, code, name, short_name, package_id, status, expire_at) VALUES
(1, 'default', '默认租户', '默认', 1, 2, DATE_ADD(NOW(), INTERVAL 100 YEAR));

INSERT INTO tenant_package (id, code, name, modules, max_users, max_storage_gb, max_employees, price_cents, is_builtin) VALUES
(1, 'free',       '免费版', JSON_ARRAY('hr_basic','workflow'),                            10,   1,    50,    0,        1),
(2, 'standard',   '标准版', JSON_ARRAY('hr','finance','contract','procurement','assets'),  100,  50,   1000,  99900,    0),
(3, 'enterprise', '企业版', JSON_ARRAY('hr','finance','contract','procurement','assets',
                                       'inventory','sales','payroll','bi','mobile'),       9999, 9999, 999999, 999900, 0);

INSERT INTO common_seq (seq_name, current_val, prefix, description) VALUES
('hr_employee_no', 1, 'E', '员工编号'),
('hr_contract_no', 1, 'C', '合同编号');
```

- [ ] **Step 6: 执行 SQL**

Run:
```bash
docker exec -i lumen-mysql mysql -uroot -proot123 lumen_db < lumen-parent/sql/V1.0.0__init_tenant_tables.sql
docker exec -i lumen-mysql mysql -uroot -proot123 lumen_db < lumen-parent/sql/V1.0.1__init_tenant_seed.sql
```

Expected: 两条 SQL 都成功执行

- [ ] **Step 7: 验证表已创建**

Run:
```bash
docker exec lumen-mysql mysql -uroot -proot123 lumen_db -e "SHOW TABLES;"
```

Expected: 看到 `tenant`, `tenant_package`, `tenant_config`, `common_seq` 等表

- [ ] **Step 8: 提交**

```bash
git add docker sql
git commit -m "feat(docker): add docker compose and init SQL"
```

---

> **篇幅限制，剩余 30+ 个 Task (lumen-platform/lumen-auth/lumen-system/lumen-org/lumen-gateway 模块 + 集成测试 + 部署) 详见后续部分。** 由于本计划为 AI 上下文展示，已包含 P0 阶段最关键的 8 个 Task 作为脚手架和示范，**剩余 Task 在生产实施时按以下结构继续**：
>
> - **Task 9-15**: lumen-platform (租户/套餐/字典/序列号服务)
> - **Task 16-25**: lumen-auth (登录/JWT/SSO/MFA)
> - **Task 26-40**: lumen-system (用户/角色/菜单/部门 CRUD + RBAC 注解)
> - **Task 41-50**: lumen-org (扩展部门/职级/汇报线/编制)
> - **Task 51-60**: lumen-gateway (路由/限流/熔断/CORS)
> - **Task 61-70**: 集成测试 + Testcontainers
> - **Task 71-80**: Docker 镜像 + K8s 部署 + CI/CD

---

## Self-Review 检查清单

- [ ] P0 阶段所有模块都在 8 个 Task 范围内（当前 8 个 Task + 续写）
- [ ] 每个 Task 有明确的文件路径
- [ ] 每个 Step 有具体可执行的代码或命令
- [ ] TDD 流程：先写测试 → 运行测试 → 实现 → 运行测试 → 提交
- [ ] 没有占位符（"TBD"/"TODO"/"待实现"）
- [ ] 命名一致：UserContext、JwtTokenProvider、RedisLock 等在所有 Task 中保持一致
- [ ] 数据库表名/列名与附录 G 完全一致

---

**下一步**：根据用户选择的执行模式，进入开发：
- 方式 1：subagent 派发（推荐）
- 方式 2：当前会话内批量执行

请告诉我选择哪种方式，或者如需继续详细化剩余 70+ Task，我也可以继续展开。
