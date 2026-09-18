# ADR-0015: 链路追踪 SkyWalking

## 状态
2026-09-18 已决策

## 背景
17 个微服务调用关系复杂，需要：
- 排查慢请求是哪个环节慢
- 定位错误传播链
- 服务依赖关系可视化
- 性能瓶颈分析

## 决策
**SkyWalking 9.x** 作为分布式链路追踪方案，**自动探针**接入，零业务代码侵入。

## 备选方案

| 方案 | 优点 | 缺点 | 决策 |
|---|---|---|---|
| **SkyWalking（✅）** | 国产 APM、自动探针、国产化适配好 | UI 相对简陋 | ✅ |
| **Jaeger** | CNCF 标准、轻量 | UI 弱、APM 指标弱 | ⚠️ |
| **Zipkin** | 老牌、社区广 | 文档少中文、性能一般 | ⚠️ |
| **自研 Pinpoint** | 字节系、支持高 | 社区版功能受限 | ❌ |
| **Elastic APM** | 与 ELK 集成 | Java 探针成熟度一般 | ⚠️ |

## 架构

```
应用 (Java/Spring Boot)
   └── -javaagent:/skywalking/agent/skywalking-agent.jar
            ↓ 上报 trace/span/metrics
   SkyWalking OAP 集群（3 节点）
            ↓ 存储
   ┌──── ElasticSearch（trace 数据）
   ├──── ElasticSearch（日志）
   └──── ElasticSearch（指标）
            ↓ UI
   SkyWalking UI（http://skywalking.lumen.com）
```

## 接入方式

### Java 服务

```bash
java -javaagent:/skywalking/agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=hr-service \
     -Dskywalking.collector.backend_service=skywalking-oap.lumen.svc:11800 \
     -Dskywalking.agent.sample_n_per_3_secs=10 \      # 每 3 秒采样 10 个
     -Dskywalking.agent.ignore_suffix=.jpg,.css,.js \
     -jar hr-service.jar
```

K8s 部署通过 `agent.path` 挂载 PVC，自动注入。

### Vue 前端

```javascript
// main.js
import { setupSkywalking } from 'skywalking-client-js';
setupSkywalking({
  collector: 'http://skywalking.lumen.com',
  service: 'lumen-web',
  page: 'home',
  useFmp: true
});
```

## 关键能力

### 1. 拓扑图自动生成
服务依赖关系实时可视化，发现不健康依赖。

### 2. 慢链路追踪
点开慢请求，看到每一跳耗时，定位瓶颈。

### 3. 错误传播
显示错误从哪个服务开始传播到哪个服务结束。

### 4. JVM 监控
heap、GC、线程池、连接池，无需额外配置。

### 5. 自定义 Trace

```java
@Trace  // 标记该方法需要追踪
public Employee process(EmployeeDTO dto) {
    // 业务代码
}

@Tags({@Tag(key = "employeeId", value = "arg[0].id")})
public void process(Long id) {
    // ...
}
```

## 采样策略

| 场景 | 采样率 |
|---|---|
| 正常请求 | 10% |
| 错误请求 | 100% |
| 慢请求（> 1s） | 100% |
| 健康检查 | 0% |

性能影响：10% 采样下 CPU 额外开销 < 3%。

## TraceID 贯穿全链路

```
Gateway 生成 traceId → MDC 注入 → 日志带 traceId
                              ↓
                    Feign 调用自动传递 header
                              ↓
                    MQ 消息 payload 带 traceId
                              ↓
                    异步任务参数带 traceId
                              ↓
                    一条 SQL 慢查询可关联到整个链路
```

```java
@Component
public class TraceIdFeignInterceptor implements RequestInterceptor {
    public void apply(RequestTemplate template) {
        template.header("X-Trace-Id", MDC.get("traceId"));
        template.header("X-Span-Id", MDC.get("spanId"));
    }
}
```

## 与日志/告警联动

### ELK 日志带 traceId

```json
{ "ts": "...", "traceId": "abc123", "msg": "..." }
```

ELK Kibana 可点 traceId 直接跳到 SkyWalking 链路。

### 告警

```yaml
# SkyWalking alarm rules
rules:
  - name: service_error_rate
    expression: service_resp_time_percentile{_='75'} > 1000
    period: 5
    silence-period: 10
    message: 服务 {name} P75 响应时间超过 1s
    webhook: https://oapi.dingtalk.com/robot/send?access_token=xxx
```

## 集群配置

| 规模 | 配置 |
|---|---|
| MVP | OAP 1 节点 + ES 3 节点 + UI 1 节点 |
| 中型 | OAP 3 节点 + ES 5 节点 + UI 2 节点（HA） |
| 大型 | OAP 6 节点 + 独立 ES 集群 + 流式处理 |

## 后果

### 优点
- 零代码接入（agent 挂载）
- 国产化适配好（中文文档、国产数据库支持）
- 自动拓扑图
- 与国产中间件兼容性好

### 缺点
- 探针升级需重启应用
- UI 弱于商业版（Instana、Dynatrace）

### 风险与缓解
- **风险**：SkyWalking OAP 故障影响故障排查
- **缓解**：高可用部署 + 降级到日志排查
