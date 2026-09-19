package com.lumen.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.message.entity.MsgSubscription;
import com.lumen.message.mapper.MsgSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final MsgSubscriptionMapper subscriptionMapper;

    /**
     * Page subscriptions for the calling user. Tenant-scoped via the
     * TenantLineInnerInterceptor. userId from the request is IGNORED — we pin
     * from {@code UserContextHolder}.
     */
    public IPage<MsgSubscription> page(int pageNum, int pageSize) {
        UserContext ctx = requireUserContext();
        Long tenantId = ctx.getTenantId() == null ? 0L : ctx.getTenantId();
        return subscriptionMapper.selectPage(
            Page.of(pageNum, pageSize),
            new LambdaQueryWrapper<MsgSubscription>()
                .eq(MsgSubscription::getUserId, ctx.getUserId())
                .eq(MsgSubscription::getTenantId, tenantId)
                .orderByDesc(MsgSubscription::getId));
    }

    public MsgSubscription create(MsgSubscription req) {
        UserContext ctx = requireUserContext();
        if (req.getEventType() == null || req.getEventType().isBlank()) {
            throw new ServiceException(400, "eventType is required");
        }
        if (req.getChannelCode() == null || req.getChannelCode().isBlank()) {
            throw new ServiceException(400, "channelCode is required");
        }
        if (req.getEnabled() == null) req.setEnabled(1);
        // 安全 #2: pin userId from context, NEVER from request body.
        MsgSubscription toCreate = new MsgSubscription();
        toCreate.setUserId(ctx.getUserId());
        toCreate.setTenantId(ctx.getTenantId() == null ? 0L : ctx.getTenantId());
        toCreate.setEventType(req.getEventType());
        toCreate.setChannelCode(req.getChannelCode());
        toCreate.setEnabled(req.getEnabled());
        subscriptionMapper.insert(toCreate);
        log.info("Subscription created id={} user={} event={} channel={}",
            toCreate.getId(), toCreate.getUserId(),
            toCreate.getEventType(), toCreate.getChannelCode());
        return toCreate;
    }

    public MsgSubscription update(Long id, MsgSubscription req) {
        UserContext ctx = requireUserContext();
        MsgSubscription existing = subscriptionMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException(404, "Subscription not found: " + id);
        }
        // 安全 #2: pin userId from context — refuse if the caller isn't the owner.
        if (!existing.getUserId().equals(ctx.getUserId())) {
            throw new ServiceException(403, "Forbidden: not your subscription");
        }
        if (req.getEventType() != null) existing.setEventType(req.getEventType());
        if (req.getChannelCode() != null) existing.setChannelCode(req.getChannelCode());
        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        subscriptionMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        UserContext ctx = requireUserContext();
        MsgSubscription existing = subscriptionMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException(404, "Subscription not found: " + id);
        }
        // 安全 #2: pin userId — refuse if not the owner.
        if (!existing.getUserId().equals(ctx.getUserId())) {
            throw new ServiceException(403, "Forbidden: not your subscription");
        }
        subscriptionMapper.deleteById(id);
    }

    /**
     * Look up enabled channels for a (user, event) pair. Returns the channel
     * codes (not full rows) — callers need them for dispatch routing.
     */
    public List<String> findEnabledChannelsForUser(Long userId, String eventType, Long tenantId) {
        if (userId == null || eventType == null || tenantId == null) return List.of();
        List<MsgSubscription> rows = subscriptionMapper.findEnabledForUser(tenantId, userId, eventType);
        return rows.stream().map(MsgSubscription::getChannelCode).toList();
    }

    private static UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}