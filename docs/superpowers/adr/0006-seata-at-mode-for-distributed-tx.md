# ADR-0006: 分布式事务 Seata AT 模式

## 状态
2026-09-18 已决策

## 背景
跨业务服务调用涉及分布式事务：
- 报销过账：扣预算 + 写凭证 + 生成付款单
- 采购入库：写库存 + 生成应付 + 更新采购单
- 薪资发放：算薪 + 银行文件 + 财务过账

需要保证这些场景的数据一致性。

## 决策
**默认 Seata AT 模式**（无侵入）；金融强一致场景（薪资、银行）单独用 **TCC 模式**自研。

## AT vs TCC 对比

| 维度 | AT 模式 | TCC 模式 |
|---|---|---|
| 侵入性 | 无（SQL 解析） | 高（3 个方法：Try/Confirm/Cancel） |
| 性能 | 中等（加锁+日志） | 高（业务控制） |
| 适用场景 | 90% 业务 | 强一致、高并发 |
| 学习成本 | 低 | 高 |

## 使用策略

| 场景 | 方案 | 理由 |
|---|---|---|
| 报销过账 | AT | 流程长、低并发、要求最终一致 |
| 采购入库 | AT | 同上 |
| 薪资发放 | **TCC 自研** | 强一致、需对账、不可重复算 |
| 银行代发 | **TCC + 幂等表** | 强一致、需回滚 |
| 跨业务查询 | **不用事务**，走 BI 同步 | 弱一致即可 |

## AT 模式注意事项

```java
@GlobalTransactional(name = "expense-submit", rollbackFor = Exception.class)
public void submitExpense(ExpenseDTO dto) {
    // 1. 写报销单
    expenseService.save(dto);
    // 2. 扣预算（远程 RPC）
    budgetFeignClient.deduct(dto.getBudgetId(), dto.getAmount());
    // 3. 生成凭证（远程 RPC）
    financeFeignClient.createVoucher(dto);
}
```

- ❌ **不**在事务方法里做 MQ 发送（可能导致已提交但 MQ 没发）
- ✅ 用 **本地消息表** + 定时扫描
- ✅ 远程调用必须用 Feign（不能 URL 直连）
- ✅ 超时时间必须配置（默认 30s）

## 后果

### 优点
- AT 模式零侵入，业务代码改动小
- Seata Dashboard 可视化事务状态
- 自动回滚 + 高可用（TC 集群）

### 缺点
- AT 模式对长事务支持弱（>10s 易锁冲突）
- 依赖 TC（Transaction Coordinator）服务，需独立部署集群
- 与 @Transactional 嵌套使用要小心

### 风险与缓解
- **风险**：TC 故障导致整个分布式事务不可用
- **缓解**：TC 至少 3 节点集群；半同步复制模式
- **风险**：脏写（两个事务改同一行）
- **缓解**：默认全局锁超时 + 业务幂等 + 数据版本号

## 相关决策
- ADR-0001 Nacos（Seata TC 注册到 Nacos）
