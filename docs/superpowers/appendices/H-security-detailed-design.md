# 附录 H：安全详细设计

> 本附录对**等保三级（GB/T 22239-2019）**的**五大要求**（安全物理环境、安全通信网络、安全区域边界、安全计算环境、安全管理）逐条对应到本系统的**具体实现**。
>
> 配套：
> - 主 spec §7 安全架构
> - ADR-0003（多租户）、ADR-0011（数据权限）

---

## H.1 安全总体目标

| 维度 | 等保三级目标 | 本系统目标 |
|---|---|---|
| **保密性** | 重要数据防泄露 | ✓ 字段级加密 + 国密算法 |
| **完整性** | 数据防篡改 | ✓ HMAC 签名 + 操作日志 |
| **可用性** | RTO ≤ 4h | ✓ RTO ≤ 30 分钟（多活） |
| **可审计** | 操作留痕 | ✓ sys_oper_log + sys_auth_audit |
| **可追溯** | 攻击可溯源 | ✓ SkyWalking TraceID + 完整审计 |

---

## H.2 物理与基础设施（外包）

> 私有部署：客户机房需达等保三级物理要求（门禁、监控、防雷、温湿度）。
> SaaS 部署：阿里云 / 华为云已通过等保三级认证，可复用其物理安全资质。

本系统不直接负责物理层，但需：
- 对部署文档明确最低要求（异亮 5.1）
- 在甲方验收时检查物理安全合规清单

---

## H.3 安全通信网络

### H.3.1 网络架构

```
┌─────────────────────────────────────────────┐
│ 用户（浏览器 / App）                          │
└───────────────┬─────────────────────────────┘
                │ HTTPS (TLS 1.2+)
                ↓
┌─────────────────────────────────────────────┐
│ CDN + WAF（边缘防护）                       │
│  - DDoS 防护                                │
│  - SQL/XSS 过滤                             │
│  - 频率限制                                 │
└───────────────┬─────────────────────────────┘
                │ HTTPS
                ↓
┌─────────────────────────────────────────────┐
│ Nginx（反向代理 + SSL 终结）               │
│  - HSTS                                    │
│  - TLS 1.2 强制                             │
│  - 安全 Header                           │
└───────────────┬─────────────────────────────┘
                │ mTLS（内部服务间）
                ↓
┌─────────────────────────────────────────────┐
│ Spring Cloud Gateway                        │
│  - JWT 校验                                 │
│  - 限流（Sentinel）                         │
│  - 黑名单过滤                               │
└───────────────┬─────────────────────────────┘
                │ mTLS（内部服务间）
                ↓
        各微服务（vpc 内）
```

### H.3.2 TLS 配置（合规要求）

```yaml
# Nginx SSL 配置（强制 TLS 1.2+，禁用弱算法）
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
ssl_prefer_server_ciphers on;
ssl_session_cache shared:SSL:10m;
ssl_session_timeout 1d;
ssl_session_tickets off;

# HSTS
add_header Strict-Transport-Security "max-age=63072000" always;
add_header X-Frame-Options SAMEORIGIN;
add_header X-Content-Type-Options nosniff;
add_header X-XSS-Protection "1; mode=block";
add_header Content-Security-Policy "default-src 'self'";
```

### H.3.3 mTLS（服务间通信）

> 所有内部服务间通信走 mTLS，防止东西向流量被窃听。

```yaml
# application.yml（每个服务）
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
      httpclient:
        ssl:
          key-store: classpath:keystore/client.p12
          key-store-password: changeit
          trust-store: classpath:keystore/truststore.p12
          trust-store-password: changeit
```

```java
// Feign 调用走 HTTPS
@FeignClient(name = "hr-service", url = "https://hr-service.lumen.svc")
public interface HrClient { ... }
```

### H.3.4 安全 Header（CSP / HSTS）

```java
@Component
public class SecurityHeaderFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self' https: wss:;");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=()");
        chain.doFilter(req, res);
    }
}
```

---

## H.4 安全区域边界

### H.4.1 边界划分

```
Internet（不可信区）
    ↕ WAF + 防 DDoS
DMZ 区（半可信）
    - Nginx / Gateway
    ↕ 防火墙 ACL + 白名单
业务区（可信）
    - 微服务
    ↕ 内部 ACL
数据区（高敏感）
    - MySQL / Redis / ES
```

### H.4.2 WAF 规则

```yaml
# 阿里云 WAF 关键规则（建议默认启用）
rules:
  - id: 1001
    name: 阻断 SQL 注入
    match:
      - query: 'union\s+select'
      - query: 'drop\s+table'
      - query: 'insert\s+into'
    action: deny
  - id: 1002
    name: 阻断 XSS
    match:
      - query: '<script>'
      - query: 'javascript:'
      - body: 'onerror\s*='
    action: deny
  - id: 1003
    name: 频率限制（IP 维度）
    match:
      - qps_per_ip: 100  # 单 IP 100 QPS
    action: challenge
  - id: 1004
    name: 阻断已知恶意 UA
    match:
      - user_agent: '(sqlmap|nikto|nmap)'
    action: deny
  - id: 1005
    name: 阻断敏感文件下载
    match:
      - path: '/etc/passwd'
      - path: '\.env'
      - path: '\.git/'
    action: deny
```

### H.4.3 防火墙策略（私有部署）

```bash
# 仅放通必要端口
iptables -A INPUT -p tcp --dport 22 -s 10.0.0.0/8 -j ACCEPT    # SSH（管理网段）
iptables -A INPUT -p tcp --dport 80,443 -j ACCEPT                # HTTP/HTTPS（外部）
iptables -A INPUT -p tcp --dport 3306 -s 10.0.0.0/8 -j ACCEPT    # MySQL（仅内部）
iptables -A INPUT -p tcp --dport 6379 -s 10.0.0.0/8 -j ACCEPT    # Redis（仅内部）
iptables -A INPUT -p icmp -j ACCEPT                              # Ping
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
iptables -A INPUT -i lo -j ACCEPT
iptables -A INPUT -j DROP                                        # 默认拒绝
```

### H.4.4 入侵检测

| 系统 | 部署位置 | 触发告警 |
|---|---|---|
| **HIDS**（主机层） | 所有应用服务器 | 文件篡改、异常进程、暴力破解 |
| **NIDS**（网络层） | 核心交换机镜像口 | 异常流量、扫描行为 |
| **WAF**（应用层） | 边缘 | SQL/XSS/CSRF 攻击 |
| **RASP**（运行时） | JVM 内 | Java 反序列化攻击 |

---

## H.5 安全计算环境（核心）

### H.5.1 身份鉴别

#### 1. 用户名密码（基础）

```yaml
# 密码策略
password:
  min_length: 12                # 等保三级要求 ≥ 8
  complexity: true              # 必须含大小写+数字+符号
  max_age_days: 90              # 90 天强制改密
  history_count: 5              # 不能与前 5 次重复
  max_fail_count: 5             # 5 次失败锁定 30 分钟
  lock_duration_minutes: 30
  bcrypt_strength: 12           # BCrypt cost factor
```

#### 2. 多因素认证（MFA）

```java
// TOTP（基于时间的一次性密码）实现
@Service
public class MfaService {
    
    // 生成密钥（首次绑定）
    public MfaBindResponse generateSecret(Long userId) {
        String secret = new TotpGenerator().generateSecret(160);  // 160 bit
        String secretEnc = aesEncryptor.encrypt(secret);          // 加密存储
        userMapper.updateMfaSecret(userId, secretEnc);
        
        String qrUri = String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=Lumen",
            "Lumen", user.getUserName(), secret);
        String qrBase64 = qrCodeGenerator.generate(qrUri);
        
        return new MfaBindResponse(secret, qrBase64);
    }
    
    // 校验 6 位 TOTP
    public boolean verify(Long userId, String code) {
        String secretEnc = userMapper.getMfaSecret(userId);
        String secret = aesDecryptor.decrypt(secretEnc);
        
        // 容忍 ±1 个时间窗口（30s × 3 = 90s）
        return new TotpGenerator().verify(secret, code, 1);
    }
}
```

**MFA 强制策略**：

| 角色 | MFA 强制 | 说明 |
|---|---|---|
| 超级管理员 | 是 | 登录必须 MFA |
| 租户管理员 | 是 | 登录必须 MFA |
| 普通用户 | 否 | 可选，但敏感操作（修改密码、转账）强制 |
| API 用户 | 否 | 使用 API Key + IP 白名单 |

#### 3. SSO（统一认证）

> 钉钉/企微/飞书采用 OAuth 2.0 + OIDC 标准协议。
> 详细流程见 ADR-0013。

```
1. 用户点击"钉钉登录"
   → 跳转钉钉授权页 https://oapi.dingtalk.com/connect/oauth2/sns_authorize
2. 用户在钉钉确认授权
   → 钉钉回调 redirect_uri 携带 authCode
3. 后端用 authCode 调 https://oapi.dingtalk.com/sns/getuserinfo_bycode
   → 拿到 userInfo.unionid
4. 在 sys_sso_account 表查 unionid
   → 命中：返回 JWT
   → 未命中：引导用户绑定（已有账号绑定 / 自动创建）
5. JWT 通过 HttpOnly Cookie 返回
```

### H.5.2 访问控制（核心）

#### 1. RBAC + 数据权限 5 级

```
sys_user (用户)
   ↓ N:N
sys_user_role (用户角色)
   ↓ N:1
sys_role (角色)
   ↓ 1:N
sys_role_menu (角色菜单权限)
   ↓
sys_menu (菜单/API 权限)

数据权限（基于角色 + 用户）：
   1. 全部（ALL）
   2. 本部门（DEPT）
   3. 本部门及下级（DEPT_AND_CHILD）
   4. 本人（SELF）
   5. 自定义（CUSTOM）→ sys_role_custom_dept
```

#### 2. MyBatis 拦截器（自动注入数据权限）

```java
@Intercepts(@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
public class DataScopeInterceptor implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 1. 当前线程取出 userContext（前面 JWT 过滤器已设置）
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            return invocation.proceed();
        }
        
        // 2. 拿到当前 SQL，解析出表名
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql().toLowerCase();
        
        // 3. 判断是否包含 @DataScope 注解（通过方法注解）
        boolean needDataScope = checkAnnotation(ms.getId());
        if (!needDataScope) {
            return invocation.proceed();
        }
        
        // 4. 根据 data_scope 拼接 WHERE
        String dataScopeSql = buildDataScopeSql(ctx, sql);
        // 改写 SQL（动态修改 boundSql）
        ...
        
        return invocation.proceed();
    }
    
    private String buildDataScopeSql(UserContext ctx, String sql) {
        String alias = extractAlias(sql);  // 提取主表别名
        switch (ctx.getDataScope()) {
            case ALL: return "";
            case DEPT: return String.format(" AND %s.dept_id = %d ", alias, ctx.getDeptId());
            case DEPT_AND_CHILD: {
                // 用物化 path LIKE
                String deptPath = orgService.getDeptPath(ctx.getDeptId());
                return String.format(" AND %s.dept_id IN (SELECT dept_id FROM sys_dept WHERE path LIKE '%s%%') ",
                    alias, deptPath);
            }
            case SELF: return String.format(" AND %s.user_id = %d ", alias, ctx.getUserId());
            case CUSTOM: {
                List<Long> deptIds = sysRoleCustomDeptMapper.listByRole(ctx.getRoleId());
                return String.format(" AND %s.dept_id IN %s ", alias, deptIds);
            }
        }
    }
}
```

```java
// 业务 Mapper 接口使用
public interface HrEmployeeMapper {
    
    @DataScope(tableAlias = "e")  // 标注此方法需要数据权限
    List<HrEmployee> listEmployees(@Param("query") EmployeeQuery query);
}
```

#### 3. 接口权限（菜单/API）

```java
// Spring Security 注解（基于注解的权限控制）
@PreAuthorize("hasAuthority('hr:employee:list')")
@GetMapping("/api/hr/employees")
public R<PageResult<HrEmployeeVO>> listEmployees(...) { ... }

@PreAuthorize("hasAuthority('hr:employee:export')")
@PostMapping("/api/hr/employees/export")
public R<String> exportEmployees(...) { ... }
```

**注解驱动 vs 数据库驱动对比**：

| 方案 | 优点 | 缺点 |
|---|---|---|
| **注解驱动**（`@PreAuthorize`） | 类型安全、IDE 友好 | 改权限需改代码 + 重启 |
| **数据库驱动**（`sys_menu.api_pattern`） | 改权限免重启 | 不安全（运行时解析） |

本系统采用**注解驱动为主 + 数据库缓存为辅**：
- 默认走注解
- 后台"菜单管理"刷新时，把注解信息回写 `sys_menu.api_pattern`
- 网关层做**粗粒度拦截**（URL pattern）
- 服务层做**细粒度校验**（`@PreAuthorize`）

#### 4. 多租户隔离（行级）

```java
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class TenantInterceptor implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            return invocation.proceed();
        }
        
        // INSERT/UPDATE 时自动填充 tenant_id
        Object parameter = invocation.getArgs()[1];
        if (parameter instanceof BaseEntity) {
            ((BaseEntity) parameter).setTenantId(ctx.getTenantId());
        }
        
        // SELECT 时改写 SQL，加 tenant_id 条件
        ...
        
        return invocation.proceed();
    }
}
```

### H.5.3 安全审计

#### 1. 操作日志（自动记录）

```java
@Aspect
@Component
public class OperLogAspect {
    
    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Exception ex = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Exception e) {
            ex = e;
            throw e;
        } finally {
            SysOperLog log = new SysOperLog();
            log.setTitle(operLog.title());
            log.setBusinessType(operLog.businessType().name());
            log.setMethod(pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName());
            log.setRequestMethod(RequestContextHolder.getRequest().getMethod());
            log.setOperUserId(UserContextHolder.getUserId());
            log.setTenantId(UserContextHolder.getTenantId());
            log.setOperUrl(RequestContextHolder.getRequest().getRequestURI());
            log.setOperIp(ServletUtil.getClientIP());
            log.setOperParam(JSON.toJSONString(pjp.getArgs()).substring(0, 2000));
            log.setJsonResult(JSON.toJSONString(result).substring(0, 2000));
            log.setStatus(ex == null ? 0 : 1);
            log.setErrorMsg(ex == null ? "" : ex.getMessage());
            log.setCostTime(System.currentTimeMillis() - start);
            log.setOperTime(LocalDateTime.now());
            
            // 异步写库
            threadPool.execute(() -> sysOperLogMapper.insert(log));
        }
    }
}

// 业务方法使用
@OperLog(title = "员工档案", businessType = BusinessType.UPDATE)
@PutMapping("/api/hr/employees/{id}")
public R<Void> updateEmployee(@PathVariable Long id, @RequestBody HrEmployeeDTO dto) { ... }
```

#### 2. 登录日志

```java
@Component
public class LoginEventListener {
    
    @EventListener
    public void onLoginSuccess(LoginSuccessEvent event) {
        SysLogininfor log = new SysLogininfor();
        log.setUserName(event.getUserName());
        log.setIp(event.getIp());
        log.setBrowser(event.getBrowser());
        log.setOs(event.getOs());
        log.setStatus("0");  // 成功
        log.setMessage("登录成功");
        sysLogininforMapper.insert(log);
    }
    
    @EventListener
    public void onLoginFailure(LoginFailureEvent event) {
        SysLogininfor log = new SysLogininfor();
        log.setUserName(event.getUserName());
        log.setIp(event.getIp());
        log.setStatus("1");  // 失败
        log.setMessage(event.getReason());
        sysLogininforMapper.insert(log);
        
        // 失败计数
        sysLoginFailMapper.insertFail(event.getUserName(), event.getIp(), event.getReason());
    }
}
```

#### 3. 数据审计（细粒度）

```java
// 关键数据变更记录前后快照
@Aspect
@Component
public class DataAuditAspect {
    
    @Around("@annotation(dataAudit)")
    public Object around(ProceedingJoinPoint pjp, DataAudit dataAudit) throws Throwable {
        // UPDATE/DELETE 前查 before
        Object before = null;
        if (dataAudit.snapshotBefore()) {
            Object id = extractId(pjp.getArgs());
            before = dataAudit.targetMapper().findById(id);
        }
        
        Object result = pjp.proceed();
        
        Object after = null;
        if (dataAudit.snapshotAfter()) {
            Object id = extractId(pjp.getArgs());
            after = dataAudit.targetMapper().findById(id);
        }
        
        SysDataAudit audit = new SysDataAudit();
        audit.setEntityName(dataAudit.entity());
        audit.setBeforeJson(JSON.toJSONString(before));
        audit.setAfterJson(JSON.toJSONString(after));
        audit.setOperator(UserContextHolder.getUserId());
        threadPool.execute(() -> sysDataAuditMapper.insert(audit));
        
        return result;
    }
}
```

### H.5.4 入侵防范

| 威胁 | 防护 | 实现 |
|---|---|---|
| **SQL 注入** | 参数化查询 + WAF | MyBatis `#{}` + 阿里云 WAF |
| **XSS** | 前端转义 + CSP | Vue v-html 禁用 + CSP 头 |
| **CSRF** | SameSite Cookie + Token | Spring Security CSRF |
| **DDoS** | CDN + WAF | 阿里云高防 |
| **暴力破解** | 登录限速 + 锁定 | 失败 5 次锁定 30 分钟 |
| **越权访问** | RBAC + 数据权限 | 注解 + 拦截器 |
| **文件上传漏洞** | 白名单 + 病毒扫描 | 后缀 + MIME + ClamAV |
| **敏感信息泄露** | 字段加密 + 日志脱敏 | AES + Logback Pattern |
| **反序列化攻击** | 黑名单 + RASP | 禁用 ObjectInputStream |
| **供应链攻击** | SCA 扫描 | 依赖扫描（OSS Risk） |

### H.5.5 恶意代码防范

- **服务器端**：ClamAV 扫描上传文件
- **客户端**：N/A（前端纯展示）
- **依赖扫描**：CI 跑 OSS Risk / Snyk
- **镜像扫描**：Trivy 扫 Docker 镜像

### H.5.6 数据完整性

#### 1. 传输完整性（TLS）

所有外部通信走 HTTPS（TLS 1.2+），自动保证。

#### 2. 存储完整性（HMAC）

```java
// 关键数据存 HMAC 签名
public class IntegrityService {
    
    public void saveImportant(HrEmployee emp) {
        emp.setHmac(generateHmac(emp));
        hrEmployeeMapper.insert(emp);
    }
    
    public boolean verify(HrEmployee emp) {
        String stored = emp.getHmac();
        String calc = generateHmac(emp);
        return MessageDigest.isEqual(stored.getBytes(), calc.getBytes());
    }
    
    private String generateHmac(HrEmployee emp) {
        String data = emp.getIdCardEnc() + emp.getMobileEnc() + emp.getBankCardEnc() + emp.getName();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_KEY.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes()));
    }
}

// 定时任务：每天扫描一遍发现篡改
@Scheduled(cron = "0 2 * * * ?")
public void scanIntegrity() {
    List<HrEmployee> all = hrEmployeeMapper.listAll();
    for (HrEmployee emp : all) {
        if (!integrityService.verify(emp)) {
            alertService.alert("数据被篡改：员工 " + emp.getName());
            auditService.logTamper(emp);
        }
    }
}
```

### H.5.7 数据保密性

#### 1. 字段加密（AES-256-GCM）

```java
@Component
public class FieldEncryptor {
    
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    
    @Autowired
    private KeyManagementService kms;  // 密钥从 KMS 取
    
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = SecureRandom.getInstanceStrong().generateSeed(IV_LEN);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, kms.getDataKey(), 
                new GCMParameterSpec(TAG_LEN, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            
            // 拼接 iv + ciphertext
            byte[] result = new byte[IV_LEN + cipherText.length];
            System.arraycopy(iv, 0, result, 0, IV_LEN);
            System.arraycopy(cipherText, 0, result, IV_LEN, cipherText.length);
            
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LEN);
            byte[] cipherText = Arrays.copyOfRange(data, IV_LEN, data.length);
            
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, kms.getDataKey(),
                new GCMParameterSpec(TAG_LEN, iv));
            
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

#### 2. 日志脱敏

```xml
<!-- logback-spring.xml -->
<conversionRule conversionWord="mask" 
                converterClass="com.lumen.common.log.MaskConverter"/>

<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %X{traceId:-} - 用户=%mask{userName} 手机=%mask{mobile} 操作=%msg%n</pattern>
```

```java
public class MaskConverter extends ClassicConverter {
    
    @Override
    public String convert(ILoggingEvent event) {
        // 从 MDC 取
        String userName = event.getMDCPropertyMap().get("userName");
        String mobile = event.getMDCPropertyMap().get("mobile");
        
        if (userName != null) {
            userName = userName.substring(0, 1) + "**";  // 张**
        }
        if (mobile != null && mobile.length() == 11) {
            mobile = mobile.substring(0, 3) + "****" + mobile.substring(7);  // 138****1234
        }
        
        return userName + " " + mobile;
    }
}
```

#### 3. 备份加密

```bash
# 数据库备份加密（aes-256-cbc）
mysqldump -u root -p lumen_db | openssl enc -aes-256-cbc -salt -pbkdf2 \
    -pass pass:$BACKUP_PASSWORD > /backup/lumen_$(date +%Y%m%d).sql.enc

# 上传到 OSS 时使用 OSS 服务端加密（SSE-KMS）
ossutil cp /backup/lumen_*.sql.enc oss://lumen-backup/ \
    --meta x-oss-server-side-encryption:KMS
```

---

## H.6 安全管理（制度层面）

### H.6.1 安全管理制度

```
├── 安全策略（一级文件）
│   ├── 信息安全总纲
│   └── 等级保护合规声明
├── 安全管理制度（二级文件）
│   ├── 人员安全管理制度
│   ├── 资产安全管理制度
│   ├── 访问控制管理制度
│   ├── 密码管理制度
│   ├── 变更管理制度
│   ├── 备份恢复管理制度
│   ├── 事件应急管理制度
│   └── 第三方安全管理制度
└── 操作规程（三级文件）
    ├── 系统操作手册
    ├── 应急响应预案
    └── 安全检查清单
```

### H.6.2 安全管理机构

- **安全主管**：1 名（CISO 角色）
- **安全管理员**：多名（每个域 1 名）
- **审计管理员**：1 名（独立于运维）

### H.6.3 人员安全管理

- **入职**：背景调查 + 安全培训 + 签署保密协议
- **在职**：每年安全培训 ≥ 4 小时
- **离职**：权限回收 + 资料归还 + 持续保密义务

### H.6.4 安全建设管理

| 阶段 | 安全要求 |
|---|---|
| **需求** | 威胁建模、安全需求评审 |
| **设计** | 安全设计评审、攻击面分析 |
| **开发** | 安全编码规范、SCA 扫描 |
| **测试** | 安全测试用例、渗透测试 |
| **上线** | 安全验收、配置基线检查 |
| **运维** | 漏洞扫描、日志审计、应急演练 |

---

## H.7 关键安全场景测试用例

### 用例 H-1：暴力破解防护

```
场景：攻击者尝试 100 次错误密码登录
预期：
  - 前 5 次：错误提示，不锁
  - 第 6 次：账号锁定 30 分钟，sys_user_session.fail_count=5
  - 后续 95 次：直接返回"账号已锁定"，不再走密码逻辑（防时序攻击）
  - sys_login_fail 表新增 100 条记录
  - sys_oper_log 记录"登录失败"
  - 告警：登录失败 > 50 次触发钉钉告警
```

### 用例 H-2：水平越权

```
场景：用户 A 尝试访问用户 B 的员工档案（/api/hr/employees/{B.id}）
预期：
  - HTTP 403 Forbidden
  - 日志记录"权限不足：A 访问 {B.id}"
  - sys_oper_log.businessType = QUERY
  - 告警：检测到水平越权尝试
```

### 用例 H-3：垂直越权

```
场景：普通员工（role=USER）尝试访问 /api/system/users（需 ADMIN）
预期：
  - HTTP 403 Forbidden
  - Spring Security @PreAuthorize 拦截
  - 日志记录
```

### 用例 H-4：CSRF 攻击

```
场景：用户已登录系统，访问恶意网站包含 <img src="https://lumen.com/api/hr/employees/delete/123">
预期：
  - 请求携带 CSRF Token，验证失败 → 400 Bad Request
  - 不会执行删除
```

### 用例 H-5：SQL 注入

```
场景：登录用户名输入 ' OR 1=1 --
预期：
  - MyBatis 使用 PreparedStatement，参数化处理
  - 把 ' OR 1=1 -- 当作字符串查询
  - 无匹配，返回登录失败
  - WAF 同时拦截
```

### 用例 H-6：XSS 攻击

```
场景：员工姓名输入 <script>alert(1)</script>
预期：
  - 前端显示时 Vue 自动转义，显示为 <script>alert(1)</script> 文本
  - CSP 头禁止 inline script 执行
  - 存储到 DB 时保留原样（用户输入），展示时由前端负责转义
```

### 用例 H-7：敏感信息泄露

```
场景：访问 /actuator/env 端点（Spring Boot Actuator）
预期：
  - Actuator 端点未暴露公网（仅内网）
  - 或 security 配置要求 ADMIN 权限
  - 即使暴露，也只暴露 health/info
```

### 用例 H-8：JWT 重放

```
场景：攻击者截获某用户的 JWT 并使用
预期：
  - JWT 默认有效期 30 分钟（access）+ 7 天（refresh）
  - 服务端检查 jti（JWT ID），如已登出则拒绝
  - 用户主动登出后，所有 session 失效
  - 系统管理员可强制踢出所有 session
```

### 用例 H-9：分布式追踪

```
场景：攻击者利用业务漏洞发起异常请求
预期：
  - SkyWalking TraceID 贯穿全链路
  - 安全事件发生时，TraceID 关联到具体租户 + 用户 + IP
  - 可在 SkyWalking UI 复现攻击路径
```

---

## H.8 渗透测试检查清单（每年至少一次）

```
1. 信息收集
   ☑ DNS 枚举
   ☑ 子域名扫描
   ☑ 端口扫描
   ☑ 指纹识别
2. 配置漏洞
   ☑ 默认口令
   ☑ 未授权访问（如 Actuator）
   ☑ CORS 配置
   ☑ 安全 Header
3. 注入漏洞
   ☑ SQL 注入（sqlmap）
   ☑ NoSQL 注入
   ☑ 命令注入
   ☑ LDAP 注入
4. 业务逻辑漏洞
   ☑ 越权（水平 + 垂直）
   ☑ 支付绕过
   ☑ 金额篡改
   ☑ 状态机绕过
   ☑ 验证码绕过
5. 客户端漏洞
   ☑ XSS（反射型 + 存储型 + DOM 型）
   ☑ CSRF
   ☑ 点击劫持
   ☑ URL 跳转
6. 服务端漏洞
   ☑ 文件上传（webshell）
   ☑ 文件下载（任意文件读取）
   ☑ SSRF
   ☑ XXE
   ☑ 反序列化
7. 中间件漏洞
   ☑ Nacos 鉴权
   ☑ Redis 未授权
   ☑ MySQL 弱口令
   ☑ ES 未授权
8. 移动端
   ☑ APK 反编译
   ☑ 证书校验
   ☑ 数据存储
   ☑ 组件暴露
```

---

## H.9 安全运营（持续）

| 活动 | 频率 | 责任人 |
|---|---|---|
| **漏洞扫描** | 每周 | 安全运维 |
| **日志审计** | 每天 | 安全管理员 |
| **日志保留** | ≥ 6 个月 | DBA |
| **备份演练** | 每季度 | DBA + 运维 |
| **应急演练** | 每半年 | CISO |
| **渗透测试** | 每年 | 外部第三方 |
| **安全培训** | 每月 | HR + 安全 |
| **SCA 扫描** | 每次构建 | CI |
| **镜像扫描** | 每次发布 | CI |
| **密码策略检查** | 每月 | 安全管理员 |

---

## H.10 合规认证清单

| 认证 | 适用 | 状态 |
|---|---|---|
| **等保三级** | 所有部署 | 必须完成 |
| **ISO 27001** | SaaS 部署 | 推荐完成 |
| **GDPR** | 欧洲客户 SaaS | 必须完成 |
| **SOC 2** | 美国客户 SaaS | 推荐完成 |
| **PCI DSS** | 涉及支付 | 如有 |
| **CCRC** | 涉密场景 | 可选 |

---

## H.11 与其他文档的关联

| 主题 | 详见 |
|---|---|
| 多租户 | ADR-0003 |
| 数据权限 5 级 | ADR-0011 |
| 分布式追踪 | ADR-0015 |
| 等保 SLA | 附录 C |
| 灰度发布（金丝雀） | ADR-0014 |
| 主安全架构 | 主 spec §7 |

---

**附录完结**。至此，本系列文档（主 spec + 8 附录 + 15 ADR）覆盖了从业务到架构、从开发到运维、从合规到安全的完整设计。

后续若需要新增附录（如：财务模块详细设计、HR 模块详细设计、性能压测报告模板等），按需扩展即可。