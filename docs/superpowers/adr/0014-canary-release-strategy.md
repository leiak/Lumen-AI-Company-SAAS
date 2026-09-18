# ADR-0014: 灰度发布策略

## 状态
2026-09-18 已决策

## 背景
17 个微服务、3000 人企业用户。大版本/不确定改动需要渐进式放量，避免一刀切带来的全量故障。

## 决策
**多维度灰度**：租户白名单 → 用户标签 → HTTP Header → 流量百分比

## 灰度规则

```yaml
# application.yml
lumen:
  gray:
    enabled: true
    rules:
      - name: v2-canary
        version: 2.1.0
        conditions:
          - type: tenant_whitelist
            values: [1, 100, 233]      # 内部租户、种子用户
          - type: user_tag
            tag: internal_staff         # 内部员工优先体验
          - type: header
            key: X-Gray-Tag
            value: canary
          - type: percentage
            ratio: 10                   # 兜底：10% 流量
        weight: 100
```

## 维度优先级

```
1. 租户白名单（指定租户走新版本）     ← 内部租户、种子用户
2. 用户标签（内部员工/测试组）         ← 主动体验
3. HTTP Header（X-Gray-Tag: canary）  ← 手动测试
4. 流量百分比（1% → 10% → 50% → 100%）← 兜底渐进放量
```

任一条件命中即走新版本。

## 放量节奏

| 阶段 | 比例 | 持续时间 | 通过标准 |
|---|---|---|---|
| Stage 0 | 0%（仅白名单） | 1 天 | 内部用户无异常反馈 |
| Stage 1 | 1% | 1 天 | 错误率 < 0.5% |
| Stage 2 | 10% | 2 天 | 错误率 < 0.3% |
| Stage 3 | 50% | 2 天 | 错误率 < 0.2% |
| Stage 4 | 100% | - | - |

每个阶段通过方可进入下一阶段。

## 实现

### 灰度标识传递

```
请求进入 → Gateway 灰度过滤器
              ↓
        根据规则判断 version: v1.0 / v2.1
              ↓
        HTTP Header X-Gray-Version 注入
              ↓
        Feign 透传到下游服务
              ↓
        各服务的版本配置决定是否启用新逻辑
```

```java
@Component
public class GrayRouterFilter implements GlobalFilter {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String version = grayRuleEngine.resolve(request);  // 返回 v1.0 / v2.1
        ServerHttpRequest mutated = request.mutate()
            .header("X-Gray-Version", version)
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
```

### 业务代码使用

```java
@Service
public class EmployeeService {
    @Autowired
    private GrayFeatureService gray;
    
    public Employee getById(Long id) {
        if (gray.isEnabled("employee-new-card", "2.1.0")) {
            return employeeV2Mapper.selectById(id);   // 新逻辑
        }
        return employeeV1Mapper.selectById(id);       // 旧逻辑
    }
}
```

## 自动回滚

```yaml
lumen:
  gray:
    auto-rollback:
      enabled: true
      rules:
        - metric: error_rate
          threshold: 0.01               # 1%
          duration: 5min                # 持续 5 分钟
          action: rollback
        - metric: p99_latency_ms
          threshold: 2000
          baseline: 1000                # 基线 2 倍
          duration: 5min
          action: rollback
        - metric: business_kpi
          name: login_success_count
          threshold: 0.5                # 跌幅 50%
          duration: 10min
          action: rollback
```

触发任一规则自动回滚 + 钉钉告警。

## 数据库变更与灰度的协同

| 灰度阶段 | DDL 阶段 |
|---|---|
| 仅白名单（0%） | 已完成 expand（新字段 NULL 默认） |
| 1% | 双写已开启（新老字段都写） |
| 10% | 开始切读（新代码读新字段） |
| 50% | 切读观察期 |
| 100% | 全量切读 |
| 1 个月后 | 清理双写逻辑 + contract（删旧字段） |

## 后果

### 优点
- 渐进放量，故障影响可控
- 自动回滚减少人工干预
- 多维度精准定位灰度用户

### 缺点
- 双写期间数据维护成本
- 灰度规则配置可能混乱（需治理）

### 风险与缓解
- **风险**：灰度规则冲突导致部分用户不一致体验
- **缓解**：规则引擎版本化管理 + 灰度评审流程
