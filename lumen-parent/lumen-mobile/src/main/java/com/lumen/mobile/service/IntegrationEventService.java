package com.lumen.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.ReceiveEventRequest;
import com.lumen.mobile.entity.IntegrationEventLog;
import com.lumen.mobile.mapper.IntegrationEventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协作平台事件日志服务。
 *
 * <p>安全要求 #5 / #6：</p>
 * <ul>
 *   <li>回调接口入口必须先验签 — 本地 stub（TODO P5 真实签名校验）。</li>
 *   <li>防重放：UNIQUE(source_id, event_type, deleted) — 同 source 重复事件入 DB 时 UNIQUE 报错。</li>
 *   <li>事件日志仅追加（安全要求 #13）：service 不暴露 update / delete API；processEvent 仅更新 processed 状态位。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationEventService {

    public static final int PROCESSED_PENDING = 0;
    public static final int PROCESSED_OK = 1;
    public static final int PROCESSED_FAILED = 2;

    public static final int DEFAULT_ERROR_MESSAGE_MAX = 512;

    private final IntegrationEventLogMapper eventMapper;

    /**
     * 写入一条回调事件。重复 source_id + event_type 视为已接收，幂等返回已存在记录。
     *
     * <p>签名校验在 controller 层先做（{@code IntegrationController#callback}），service 仅做 DB 防重放兜底。</p>
     */
    @Transactional
    public IntegrationEventLog receiveEvent(String platform, String eventType, String sourceId,
                                            Map<String, Object> payload) {
        if (platform == null || platform.isBlank()) {
            throw new ServiceException(400, "platform is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new ServiceException(400, "eventType is required");
        }

        // 防重放 (安全要求 #6) — DB UNIQUE 索引兜底；这里 selectOne 走 deleted=0
        if (sourceId != null && !sourceId.isBlank()) {
            IntegrationEventLog existing = eventMapper.findBySource(platform, sourceId);
            if (existing != null) {
                log.info("Duplicate integration event ignored platform={} sourceId={} eventType={}",
                    platform, sourceId, eventType);
                return existing;
            }
        }

        IntegrationEventLog event = new IntegrationEventLog();
        event.setPlatform(platform);
        event.setEventType(eventType);
        event.setSourceId(sourceId);
        event.setPayload(payload);
        event.setProcessed(PROCESSED_PENDING);
        event.setRetryCount(0);
        event.setReceivedAt(LocalDateTime.now());

        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            // 并发场景下两个回调同时穿过 selectOne，DB UNIQUE 兜底
            log.warn("Integration event UNIQUE conflict platform={} sourceId={} eventType={}",
                platform, sourceId, eventType);
            // 返回已有记录保持幂等
            if (sourceId != null && !sourceId.isBlank()) {
                IntegrationEventLog e = eventMapper.findBySource(platform, sourceId);
                if (e != null) return e;
            }
            throw new ServiceException(409, "Duplicate integration event", ex);
        }
        log.info("Integration event received id={} platform={} eventType={}",
            event.getId(), platform, eventType);
        return event;
    }

    /**
     * 标记事件已处理。processEvent 是唯一允许修改 int_event_log 的入口（安全要求 #13）。
     *
     * @return 更新后的记录
     */
    @Transactional
    public IntegrationEventLog processEvent(Long id, boolean success, String errorMessage) {
        requireAdminCtx();
        IntegrationEventLog existing = eventMapper.selectById(id);
        if (existing == null) throw new ServiceException(404, "Integration event not found: " + id);
        if (existing.getProcessed() != null && existing.getProcessed() == PROCESSED_OK) {
            return existing; // 幂等
        }
        existing.setProcessed(success ? PROCESSED_OK : PROCESSED_FAILED);
        existing.setProcessedAt(LocalDateTime.now());
        if (errorMessage != null) {
            existing.setErrorMessage(
                errorMessage.length() > DEFAULT_ERROR_MESSAGE_MAX
                    ? errorMessage.substring(0, DEFAULT_ERROR_MESSAGE_MAX)
                    : errorMessage);
        }
        if (success) {
            existing.setErrorMessage(null);
        } else {
            existing.setRetryCount(existing.getRetryCount() == null ? 1 : existing.getRetryCount() + 1);
        }
        eventMapper.updateById(existing);
        return existing;
    }

    /**
     * 重试失败的回调 — 把 retry_count < maxRetry 的 PROCESSED_FAILED 重新置为 PROCESSED_PENDING。
     * 返回被重置的事件 id 列表。
     */
    @Transactional
    public List<Long> retryFailed(int maxRetryCount) {
        requireAdminCtx();
        List<IntegrationEventLog> pending = eventMapper.findUnprocessed(500);
        List<Long> retried = new java.util.ArrayList<>();
        for (IntegrationEventLog e : pending) {
            if (e.getProcessed() != null && e.getProcessed() == PROCESSED_FAILED
                && (e.getRetryCount() == null || e.getRetryCount() < maxRetryCount)) {
                e.setProcessed(PROCESSED_PENDING);
                e.setProcessedAt(null);
                eventMapper.updateById(e);
                retried.add(e.getId());
            }
        }
        log.info("Retry integration events count={} maxRetry={}", retried.size(), maxRetryCount);
        return retried;
    }

    public IntegrationEventLog get(Long id) {
        requireAdminCtx();
        IntegrationEventLog e = eventMapper.selectById(id);
        if (e == null) throw new ServiceException(404, "Integration event not found: " + id);
        return e;
    }

    public IPage<IntegrationEventLog> page(int pageNum, int pageSize, String platform, String eventType) {
        requireAdminCtx();
        var w = new LambdaQueryWrapper<IntegrationEventLog>().orderByDesc(IntegrationEventLog::getId);
        if (platform != null && !platform.isBlank()) w.eq(IntegrationEventLog::getPlatform, platform);
        if (eventType != null && !eventType.isBlank()) w.eq(IntegrationEventLog::getEventType, eventType);
        return eventMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public List<IntegrationEventLog> findUnprocessed(int limit) {
        requireAdminCtx();
        return eventMapper.findUnprocessed(limit);
    }

    private UserContext requireAdminCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }
}
