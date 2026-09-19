package com.lumen.bi.service;

import com.lumen.bi.entity.BiMetric;
import com.lumen.bi.mapper.BiMetricMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据同步服务。
 *
 * <p>安全要求 #16: triggerSync 仅 admin (controller 层 @PreAuthorize)。
 * TODO P5: 实际 ETL 同步到 Doris/ClickHouse。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    private final BiMetricMapper metricMapper;

    // 内存状态 (TODO P5: 持久化到 Redis)
    private final Map<String, SyncStatus> statusMap = new HashMap<>();

    /**
     * 安全要求 #16: 触发同步。仅 admin。
     * @return { metricCode, status, lastSyncAt }
     */
    public Map<String, Object> triggerSync(String metricCode) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        BiMetric metric = metricMapper.findByCode(metricCode);
        if (metric == null) {
            throw new ServiceException(404, "Metric not found: " + metricCode);
        }
        // 安全要求 #1: 跨租户 404
        if (ctx.getTenantId() != null && !ctx.getTenantId().equals(metric.getTenantId())) {
            throw new ServiceException(404, "Metric not found: " + metricCode);
        }
        SyncStatus s = new SyncStatus(metricCode, STATUS_RUNNING, LocalDateTime.now(), null);
        statusMap.put(metricCode, s);
        // TODO P5: 实际 ETL 同步到 Doris/ClickHouse
        log.info("Sync triggered metric={} by user={}", metricCode, ctx.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("metricCode", metricCode);
        result.put("status", s.status());
        result.put("triggeredAt", s.lastSyncAt());
        return result;
    }

    public Map<String, Object> getSyncStatus(String metricCode) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        BiMetric metric = metricMapper.findByCode(metricCode);
        if (metric == null) {
            throw new ServiceException(404, "Metric not found: " + metricCode);
        }
        if (ctx.getTenantId() != null && !ctx.getTenantId().equals(metric.getTenantId())) {
            throw new ServiceException(404, "Metric not found: " + metricCode);
        }
        SyncStatus s = statusMap.getOrDefault(metricCode,
            new SyncStatus(metricCode, STATUS_IDLE, null, null));

        Map<String, Object> result = new HashMap<>();
        result.put("metricCode", metricCode);
        result.put("status", s.status());
        result.put("lastSyncAt", s.lastSyncAt());
        result.put("lastError", s.lastError());
        return result;
    }

    public record SyncStatus(String metricCode, String status,
                             LocalDateTime lastSyncAt, String lastError) {}
}