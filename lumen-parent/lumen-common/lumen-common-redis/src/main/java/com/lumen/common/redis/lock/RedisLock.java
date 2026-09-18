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

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String UNLOCK_SCRIPT_TEXT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
        new DefaultRedisScript<>(UNLOCK_SCRIPT_TEXT, Long.class);

    public <T> T tryLock(String key, Duration timeout, Supplier<T> action) {
        String lockKey = "lumen:lock:" + key;
        String lockValue = UUID.randomUUID().toString();
        boolean ok = Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeout));
        if (!ok) {
            log.debug("Lock acquire failed for key: {}", key);
            throw new RuntimeException("获取锁失败: " + key);
        }
        try {
            return action.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
            log.debug("Released lock: {}", key);
        }
    }
}