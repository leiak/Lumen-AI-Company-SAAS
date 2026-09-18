# ADR-0004: 审批流自研状态机 + Flowable 双方案

## 状态
2026-09-18 已决策

## 背景
17 个业务模块的审批需求差异巨大：
- 简单审批：请假、转正、报销（80% 场景）
- 复杂 BPMN：采购定标、合同会签、离职交接（20% 场景）

需要决定审批引擎实现方案。

## 决策
**双引擎方案**：简单审批走自研状态机，复杂 BPMN 走 Flowable 7.x。统一抽象 `ApprovalEngine` 接口，调用方无感知。

```java
public interface ApprovalEngine {
    ProcessInstance start(StartCmd cmd);
    void approve(ApproveCmd cmd);
    void reject(RejectCmd cmd);
    void transfer(TransferCmd cmd);
    void addSign(AddSignCmd cmd);
    List<Task> queryTodos(QueryTodoCmd cmd);
}

@Component("stateMachineEngine")   // 自研，覆盖 80% 场景
@Component("flowableEngine")       // Flowable，覆盖 20% 复杂场景
```

## 选型理由

| 引擎 | 覆盖场景 | 实现成本 | 学习成本 |
|---|---|---|---|
| **自研状态机** | 单线审批、条件分支、自动通过 | 极低（5-10 行代码搞定） | 0 |
| **Flowable 7.x** | 会签、或签、子流程、网关、定时器、多实例 | 中等（需建表、配置流程） | 中 |
| **Camunda 7** | 同 Flowable，但商业版收费 | 中 | 中 |
| **Activiti 7** | 老牌项目 | 中 | 中（文档少） |
| **钉钉/飞书自带审批** | 极简场景 | 0 | 0 |

**为什么不全用 Flowable**：
- 80% 简单审批用 Flowable 是高射炮打蚊子
- Flowable 24 张表 + 启动慢 + 调优复杂
- 自研可控性更强，能精细优化

**为什么不选钉钉审批**：
- 钉钉审批无法深度集成业务数据（如请假时长自动计算）
- 跨组织数据隔离困难

## 后果

### 优点
- 80% 场景极简实现：状态机一行代码
- 20% 复杂场景有 Flowable 兜底
- 统一接口，业务调用无感知
- 流程设计器可二选一（自研前端用 LogicFlow）

### 缺点
- 需要维护两套引擎的运维知识
- 流程数据可能分散在两张表（`wf_instance` 与 Flowable 自带 `ACT_*` 表）

### 风险与缓解
- **风险**：Flowable 与 Spring Boot 3 兼容性
- **缓解**：锁版本到 7.1.0+，官方已支持 Spring Boot 3

## 关键实现要点
- 任务中心统一视图：自研 + Flowable 任务合并展示
- 历史数据双写：审计可查完整流程轨迹
- 表单挂接：业务表单（JSON Schema）+ 流程变量双向绑定

## 相关决策
- ADR-0005 移动端方案（审批在 App 上的体验）
