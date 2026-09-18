# ADR-0012: 缓存三级架构 + Redis Cluster

## 状态
2026-09-18 已决策

## 背景
业务读多写少（如员工档案、部门树、字典），需要缓存减少 DB 压力。

## 决策
**三级缓存架构**：本地 Caffeine → Redis Cluster → DB

```
请求 → Caffeine(本地,1min) → Redis Cluster(分布式,30min) → MySQL
                  ↑                    ↑                       ↑
              命中率 80%           命中率 95%               总数据
```

## 各层职责

| 层级 | 库 | TTL | 命中场景 | 失效策略 |
|---|---|---|---|---|
| L1 本地 | Caffeine | 1 min | 同实例高频访问 | 写后失效（Clear） |
| L2 分布式 | Redis Cluster | 30 min | 跨实例共享 | 写后失效（Del） |
| L3 DB | MySQL | 永久 | 兜底 | - |

## Key 命名规范

```
lumen:{module}:{bizType}:{bizId}
lumen:hr:employee:12345
lumen:hr:dept:tree:{tenantId}
lumen:lock:hr:employee:12345
lumen:session:{userId}
```

**强制**：`{tenantId}` 必须出现在跨租户场景的 key 里。

## 更新策略

### 读多写少（90% 场景）—— Cache-Aside

```java
public Employee getById(Long id) {
    String key = "lumen:hr:employee:" + id;
    Employee e = caffeineCache.get(key);
    if (e != null) return e;
    
    e = redisCache.get(key);
    if (e != null) {
        caffeineCache.put(key, e);
        return e;
    }
    
    e = employeeMapper.selectById(id);
    if (e != null) {
        redisCache.put(key, e, 30, MINUTES);
        caffeineCache.put(key, e);
    }
    return e;
}
```

### 写多读少 —— Write-Through + 失效

```java
@Transactional
public void update(EmployeeDTO dto) {
    employeeMapper.updateById(dto);   // 先写 DB
    String key = "lumen:hr:employee:" + dto.getId();
    redisCache.delete(key);           // 再清缓存（延迟双删）
    caffeineCache.invalidate(key);
}
```

### 强一致 —— 分布式锁 + 延迟双删

```java
public void updateStrong(EmployeeDTO dto) {
    String lockKey = "lumen:lock:hr:employee:" + dto.getId();
    RLock lock = redisson.getLock(lockKey);
    lock.lock();
    try {
        employeeMapper.updateById(dto);
        redisCache.delete(key);
        Thread.sleep(500);            // 延迟双删
        redisCache.delete(key);
        caffeineCache.invalidate(key);
    } finally {
        lock.unlock();
    }
}
```

## 缓存穿透 / 击穿 / 雪崩对策

| 问题 | 方案 |
|---|---|
| **穿透**（查不存在的数据） | 布隆过滤器 + 缓存空值（短 TTL 5min） |
| **击穿**（热点 key 过期瞬间高并发） | 分布式锁 + 逻辑过期（不设 TTL，存逻辑过期时间） |
| **雪崩**（大量 key 同时过期） | TTL 加随机偏移（30min + random(0, 5min)） |

## 禁止项（Code Review 红线）

- ❌ **不缓存 List/PageResult**（数据陈旧 + 内存爆炸）
- ❌ **不用 List/Set 当 key**（hash 冲突难排查）
- ❌ **不在缓存里塞复杂对象图**（只缓存简单 ID + JSON）
- ❌ **不缓存敏感数据明文**（身份证/银行卡等加密后再缓存，或不缓存）
- ❌ **不跨租户共享 key**（必须 tenant_id 隔离）

## 集群配置

| 规模 | 配置 |
|---|---|
| MVP | 3 主 3 从（最小集群） |
| 中型 | 6 主 6 从，分两集群（业务 + 会话） |
| 大型 | 独立 Redis 集群 + 持久化 + 监控告警 |

## 后果

### 优点
- 简单三级架构，团队易理解
- Caffeine 命中率 80% 极大减轻 Redis 压力
- Redisson 提供完整分布式锁实现

### 缺点
- 多级一致性问题（本地缓存与 Redis 可能短暂不一致）
- Caffeine 占用堆内存（注意 JVM 调优）

### 风险与缓解
- **风险**：Redis 故障导致缓存雪崩
- **缓解**：熔断降级（直接走 DB）+ 限流 + 多级兜底
- **风险**：本地缓存无法集群共享（不同实例数据可能不一致）
- **缓解**：短 TTL（1min）保证最终一致
