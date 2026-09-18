# ADR-0010: API 响应包装与错误码体系

## 状态
2026-09-18 已决策

## 背景
17 个业务模块 + 6 个基础服务需统一的 API 响应格式，便于前端统一处理、做国际化、做告警分级。

## 决策
**统一响应包装 + 5 位错误码体系**

## 响应格式

```json
// 成功
{ "code": 0, "message": "ok", "data": { ... }, "traceId": "abc123" }

// 失败
{ "code": 10001, "message": "员工 12345 不存在", "data": null, "traceId": "abc123" }

// 分页
{ "code": 0, "message": "ok", "data": { "total": 1523, "pageNum": 1, "pageSize": 20, "rows": [...] }, "traceId": "abc123" }
```

## 错误码编码规则

```
{2位模块}{1位类型}{2位序号}
```

| 模块段 | 模块 |
|---|---|
| 00 | 通用（auth/param/system） |
| 01 | 平台/租户 |
| 02 | 工作流 |
| 03 | 组织 |
| 04 | HR |
| 05 | 财务 |
| 06 | 资产 |
| 07 | 采购 |
| 08 | 合同 |
| 09 | 库存 |
| 10 | 销售/CRM |
| 11 | 薪资 |
| 12 | BI |
| 13 | 文件 |
| 14 | 消息 |
| 99 | 系统兜底 |

| 类型 | 含义 |
|---|---|
| A | 业务校验（如"员工不存在"） |
| B | 状态非法（如"已离职员工无法删除"） |
| C | 认证授权 |
| D | 参数错误 |
| E | 第三方调用失败 |
| F | 系统/网络 |

## 异常体系

```java
public class BizException extends RuntimeException {
    private final String code;
    private final Object[] args;   // i18n 参数
}

public class NotFoundException extends BizException {        // → 404
public class AuthException extends BizException {           // → 401
public class PermissionException extends BizException {     // → 403
public class StateException extends BizException {          // → 409
public class ValidationException extends BizException {     // → 400
public class ThirdPartyException extends BizException {     // → 502
```

## 全局处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public R<Void> handle(NotFoundException e) {
        log.warn("not found: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage(), e.getArgs());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handle(Exception e) {
        log.error("internal error", e);
        return R.fail("99F001", "系统繁忙，请稍后重试");
    }
}
```

## 核心规则（Code Review 红线）

- ❌ **绝不** `try-catch` 后 `return R.fail()` 吞掉异常
- ✅ **直接** `throw new NotFoundException("04A001", employeeId)`
- ❌ **绝不**返回未包装的原始数据
- ✅ 所有 Controller 返回 `R<T>` 或 `R<PageResult<T>>`
- ❌ **绝不**用 HTTP 状态码表达业务错误（永远 200，业务错误在 code 字段）

## i18n 友好

错误码不变，文案走资源文件：

```yaml
# messages_zh_CN.properties
biz.04A001=员工 {0} 不存在
biz.05B002=凭证借贷不平，借方 {0} 贷方 {1}
```

前端按 `Accept-Language` 返回不同文案。

## 后果

### 优点
- 前端统一处理：code !== 0 就提示用户
- 后端统一监控：可按错误码做 Grafana 仪表盘 + 告警
- i18n 友好

### 缺点
- 错误码需提前规划（占位 → 文档化）
- 新人易在 Controller 里 return R.fail() 而不抛异常
