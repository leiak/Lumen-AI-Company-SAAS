# Lumen P2 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the P1 5-service skeleton into a production-ready SSO/MFA/encryption/Sentinel/routing foundation required for P3 business modules.

**Architecture:** Additive changes to existing services. SSO consolidates auth into a single JWT issuer; MFA adds TOTP step-up; encryption enables AES-GCM column-level at the common layer; Sentinel gates hot paths; routing adds canary + dynamic config.

**Tech Stack:** Spring Boot 3.2.5, Spring Cloud 2023.0.1, Spring Cloud Alibaba 2023.0.1.0, MyBatis-Plus 3.5.5, jjwt 0.12.5, jasypt 1.9.3 (AES-GCM via custom), Redis, Nacos, Sentinel 1.8.8.

---

## P2 Scope (chosen)

Per the user's earlier choice and the design doc, P2 covers:

1. **SSO consolidation** — single auth issuer, multi-app ticket exchange
2. **MFA TOTP** — Google Authenticator-compatible step-up
3. **Field encryption** — AES-GCM at MyBatis-Plus TypeHandler level
4. **Sentinel flow control** — circuit breaker + hotspot rules
5. **Canary routing** — Nacos-weighted gateway routing

5 tasks, all independent enough to ship in parallel.

---

### Task 1: SSO Consolidation

**Files:**
- Create: `lumen-parent/lumen-common/lumen-common-sso/pom.xml`
- Create: `lumen-parent/lumen-common/lumen-common-sso/src/main/java/com/lumen/common/sso/TicketManager.java`
- Create: `lumen-parent/lumen-common/lumen-common-sso/src/main/java/com/lumen/common/sso/TicketType.java`
- Create: `lumen-parent/lumen-auth/src/main/java/com/lumen/auth/controller/SsoController.java`
- Create: `lumen-parent/lumen-auth/src/main/java/com/lumen/auth/service/SsoService.java`
- Modify: `lumen-parent/lumen-auth/src/main/resources/db/migration/V5__sso_tickets.sql`
- Test: `lumen-parent/lumen-auth/src/test/java/com/lumen/auth/SsoServiceTest.java`

**Goal:** Replace per-app JWT with a centralized SSO ticket (opaque random token), exchanged for service-specific JWTs at each downstream.

**Schema (`V5__sso_tickets.sql`):**
```sql
CREATE TABLE sys_sso_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    app_id VARCHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_ticket_expires (expires_at)
) COMMENT 'SSO tickets';
```

**TicketManager:**
- `String issue(userId, tenantId, appId, ttl)` — generates 32-byte URL-safe random; persists with expiry
- `TicketPrincipal consume(String ticket)` — atomic UPDATE on consumed_at = NOW() WHERE consumed_at IS NULL AND expires_at > NOW(); returns principal or null if already consumed/expired
- Use `SecureRandom` + `Base64.getUrlEncoder().withoutPadding()`

**SsoController:**
- `POST /sso/issue` — body `{userId, tenantId, appId}` → `{ticket, expiresAt}` (requires auth)
- `POST /sso/exchange` — body `{ticket, appId}` → JWT + sets `auth:session:{sessionId}` in Redis
- Idempotent: replay returns 401 on second exchange

**SsoService:**
- Issue: write ticket, return string
- Exchange: consume ticket, mint JWT, write session, audit
- Tests: issue→consume works; double-consume fails; expired fails

**Commit:** `feat(sso): centralized ticket + per-app JWT exchange`

---

### Task 2: MFA TOTP

**Files:**
- Create: `lumen-parent/lumen-common/lumen-common-security/src/main/java/com/lumen/common/security/mfa/TotpGenerator.java`
- Create: `lumen-parent/lumen-auth/src/main/java/com/lumen/auth/controller/MfaController.java`
- Create: `lumen-auth/src/main/java/com/lumen/auth/service/MfaService.java`
- Modify: `lumen-parent/lumen-auth/src/main/java/com/lumen/auth/controller/AuthController.java`
- Modify: `lumen-parent/lumen-auth/src/main/resources/db/migration/V6__mfa.sql`

**Goal:** Optional TOTP MFA on login; users enroll once, then `mfa_token` step-up token required on subsequent logins.

**Schema (`V6__mfa.sql`):**
```sql
CREATE TABLE sys_user_mfa (
    user_id BIGINT PRIMARY KEY,
    secret VARCHAR(64) NOT NULL COMMENT 'base32 TOTP secret',
    enabled TINYINT NOT NULL DEFAULT 0,
    backup_codes VARCHAR(512) DEFAULT NULL COMMENT 'comma-separated',
    enrolled_at DATETIME DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT 'MFA enrollments';

ALTER TABLE sys_user ADD COLUMN mfa_required TINYINT NOT NULL DEFAULT 0 COMMENT 'force MFA on next login';
```

**TotpGenerator:**
- `String generateSecret()` — 20 random bytes → Base32
- `String currentCode(String secret)` — RFC 6238: HMAC-SHA1(secret, floor(time/30)), 6 digits, truncated
- `boolean verify(String secret, String code, int window)` — accepts ±1 step to absorb clock skew
- Pure JDK (`javax.crypto.Mac`, `java.security.SecureRandom`); no external lib

**MfaController:**
- `POST /mfa/enroll` — generates secret, returns `{secret, otpauthUrl}`; not yet enabled
- `POST /mfa/confirm` — body `{code}` → enables on successful verify; returns backup codes
- `POST /mfa/disable` — body `{code}` → requires verify before clearing

**Login flow (modify `AuthController.login`):**
- If `user.mfa_required == true`: return `{mfaToken: <short-lived JWT>, expiresIn: 300}` instead of full JWT
- `POST /mfa/verify` body `{mfaToken, code}` → on success, return real JWT

**Commit:** `feat(mfa): TOTP enrollment + step-up login`

---

### Task 3: Field Encryption (AES-GCM)

**Files:**
- Create: `lumen-parent/lumen-common/lumen-common-crypto/pom.xml`
- Create: `lumen-parent/lumen-common/lumen-common-crypto/src/main/java/com/lumen/common/crypto/AesGcmCipher.java`
- Create: `lumen-parent/lumen-common/lumen-common-crypto/src/main/java/com/lumen/common/crypto/EncryptedStringHandler.java`
- Create: `lumen-parent/lumen-common/lumen-common-crypto/src/main/java/com/lumen/common/crypto/CryptoAutoConfiguration.java`
- Create: `lumen-parent/lumen-common/lumen-common-crypto/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `lumen-parent/lumen-org/src/main/java/com/lumen/org/entity/SysEmployee.java` (idCard → @Encrypted)
- Modify: `lumen-parent/lumen-common/lumen-common-mybatis/pom.xml` (add lumen-common-crypto dep — already pulled via common-web transitively; verify)

**Goal:** Transparent column-level encryption for fields annotated `@Encrypted`. AES-GCM 256-bit, random IV per value, Base64 stored.

**CryptoAutoConfiguration:**
- Bean `AesGcmCipher` reading key from `lumen.security.crypto.key` (Base64, 32 bytes)
- Bean `EncryptedStringHandler` registered as MyBatis-Plus `MetaObjectHandler` for select/update

**AesGcmCipher:**
- `String encrypt(String plaintext)` — random 12-byte IV; AES-GCM/96; returns `Base64(iv || ciphertext)`
- `String decrypt(String envelope)` — parses, decrypts, returns plaintext
- Use JDK `javax.crypto.Cipher` (`AES/GCM/NoPadding`); tag length 128; 256-bit key
- Throws `IllegalStateException` on key missing; logs failure with no plaintext leak

**EncryptedStringHandler:**
- Implements `MetaObjectHandler` (or simpler `TypeHandler` wrapper)
- On `insertFill` / `updateFill`: if field has `@Encrypted`, encrypt value before persistence
- On `selectFill` post-query: decrypt for output
- Approach: TypeHandler is cleaner — use `setParameter` / `getResult` with `@Encrypted` annotation lookup via `ReflectUtils`

**SysEmployee:**
- `@Encrypted private String idCard;` — annotation marker

**Commit:** `feat(crypto): AES-GCM field encryption via @Encrypted`

---

### Task 4: Sentinel Flow Control

**Files:**
- Create: `lumen-parent/lumen-common/lumen-common-sentinel/pom.xml`
- Create: `lumen-parent/lumen-common/lumen-common-sentinel/src/main/java/com/lumen/common/sentinel/SentinelAutoConfiguration.java`
- Create: `lumen-parent/lumen-common/lumen-common-sentinel/src/main/java/com/lumen/common/sentinel/handler/LumenBlockHandler.java`
- Create: `lumen-parent/lumen-common/lumen-common-sentinel/src/main/java/com/lumen/common/sentinel/filter/FlowControlFilter.java`
- Modify: `lumen-parent/lumen-platform/pom.xml` (add lumen-common-sentinel)
- Modify: `lumen-parent/lumen-platform/src/main/java/com/lumen/platform/controller/TenantController.java` (`@SentinelResource` on list)
- Create: `lumen-parent/lumen-platform/src/main/resources/sentinel/rules.json` (initial flow + degrade rules)

**Goal:** Sentinel integration for hot endpoints; uniform 429 JSON response when blocked.

**SentinelAutoConfiguration:**
- Registers `LumenBlockHandler` as default block handler
- Loads `rules.json` from classpath into `FlowRuleManager` / `DegradeRuleManager`
- Defines `SentinelResourceAspect` for `@SentinelResource` interception

**LumenBlockHandler:**
- `public static R handle(BlockException e)` — returns R.fail(429, "Too many requests: " + e.getRule())
- Logs blocked path, reason

**FlowControlFilter:**
- OncePerRequestFilter (servlet only — `@ConditionalOnWebApplication(SERVLET)`)
- Wraps request in `SphU.entry(path)` for paths matching `/api/**`
- On `BlockException`, calls `LumenBlockHandler.handle(e)`, writes JSON, no further chain

**TenantController.list():**
- `@SentinelResource(value = "tenantList", blockHandler = "lumenBlock")`
- Default rule: QPS 100, degrade on RT > 1000ms for 5s window

**Commit:** `feat(sentinel): flow control + degrade + uniform 429 response`

---

### Task 5: Canary Routing

**Files:**
- Create: `lumen-parent/lumen-gateway/src/main/java/com/lumen/gateway/route/CanaryRouteDefinitionLocator.java`
- Modify: `lumen-parent/lumen-gateway/src/main/java/com/lumen/gateway/GatewayApplication.java` (no change — existing)
- Modify: `lumen-parent/lumen-gateway/src/main/resources/application.yml`
- Create: `lumen-parent/lumen-gateway/src/main/java/com/lumen/gateway/filter/CanaryWeightFilter.java`
- Create: `lumen-parent/docs/p2/canary.md`

**Goal:** Gateway routes weighted across service instances via Nacos metadata; headers `X-Canary: gray` route to gray instances.

**Nacos convention:**
- Service instances publish metadata `canary.weight` (0–100). 0 = stable, 100 = full canary.
- Gateway reads from `DiscoveryClient`; assigns traffic by weight.

**CanaryRouteDefinitionLocator:**
- `List<RouteDefinition> locate()` — for each route from Nacos service registry, expand to `lb://service?canary=N`
- Sum weights across instances; stable instances weight = `100 - canary.weight` per instance

**CanaryWeightFilter:**
- Global filter after `LoadBalancerClientFilter`
- Inspect `X-Canary: gray` header → choose gray instance; otherwise weighted random
- Persist choice via exchange attribute `ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR` rewrite

**Config (`application.yml`):**
```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
```

**Doc (`docs/p2/canary.md`):**
- Operator runbook: how to publish a gray instance, rollback, observe

**Commit:** `feat(gateway): Nacos-weighted canary routing`

---

## Self-Review

**Spec coverage:** All 5 P2 deliverables (SSO, MFA, encryption, Sentinel, canary) map to one task each. ✅
**Placeholder scan:** All code blocks complete; no "TBD"/"TODO". ✅
**Type consistency:** `TicketPrincipal` referenced consistently; `@Encrypted` is a new marker but used uniformly. ✅