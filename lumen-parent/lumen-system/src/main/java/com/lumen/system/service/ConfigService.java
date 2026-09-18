package com.lumen.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.system.entity.SysConfig;
import com.lumen.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private static final String REDIS_CONFIG_PREFIX = "sys:config:";
    private static final Duration REDIS_CONFIG_TTL = Duration.ofMinutes(10);

    private final SysConfigMapper configMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public IPage<SysConfig> list(int pageNum, int pageSize, String keyword) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
            .orderByDesc(SysConfig::getConfigId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysConfig::getConfigName, keyword)
                .or().like(SysConfig::getConfigKey, keyword));
        }
        return configMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public String getValue(String key) {
        // Try cache first
        try {
            String cached = stringRedisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + key);
            if (cached != null) return cached;
        } catch (Exception ignored) { /* Redis down — fall through */ }

        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        if (c == null) throw new ServiceException(404, "Config not found: " + key);
        try {
            stringRedisTemplate.opsForValue().set(REDIS_CONFIG_PREFIX + key, c.getConfigValue(), REDIS_CONFIG_TTL);
        } catch (Exception ignored) {}
        return c.getConfigValue();
    }

    public SysConfig update(Long id, String configValue) {
        SysConfig c = configMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Config not found: " + id);
        c.setConfigValue(configValue);
        configMapper.updateById(c);
        // Invalidate cache
        try {
            stringRedisTemplate.delete(REDIS_CONFIG_PREFIX + c.getConfigKey());
        } catch (Exception ignored) {}
        return c;
    }
}
