package com.lumen.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads active sessions cached in Redis by auth-service at key 'auth:session:{sessionId}'.
 * Value format: "userId:tenantId"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private static final String REDIS_SESSION_KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate stringRedisTemplate;

    public List<Map<String, String>> list(int limit) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            ScanOptions opts = ScanOptions.scanOptions().match(REDIS_SESSION_KEY_PREFIX + "*").count(100).build();
            try (Cursor<byte[]> cursor = stringRedisTemplate.execute(
                    (RedisCallback<Cursor<byte[]>>) conn -> conn.scan(opts))) {
                int count = 0;
                while (cursor != null && cursor.hasNext() && count < limit) {
                    byte[] keyBytes = cursor.next();
                    String key = new String(keyBytes);
                    String sessionId = key.substring(REDIS_SESSION_KEY_PREFIX.length());
                    String value = stringRedisTemplate.opsForValue().get(key);
                    Map<String, String> row = new HashMap<>();
                    row.put("sessionId", sessionId);
                    if (value != null) {
                        String[] parts = value.split(":");
                        if (parts.length >= 2) {
                            row.put("userId", parts[0]);
                            row.put("tenantId", parts[1]);
                        }
                    }
                    result.add(row);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("online user scan failed: {}", e.getMessage());
        }
        return result;
    }
}
