package com.lumen.mobile.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.MobPushToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 推送服务（聚合 PushTokenService）。
 *
 * <p>P4 阶段：仅记日志 + 返回成功；P5 接入真实推送 SDK（FCM / APNs / HMS Push）。
 * 安全要求 #11：pushToUser 必须有 token；否则抛 400（前端 bug 或 token 未注册）。</p>
 *
 * <p>跨服务调用（workflow / notification）留 TODO（P4 不接 Feign）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushTokenService pushTokenService;

    /**
     * 推送至单个用户。该用户若没有任何 active token，抛 400（安全要求 #11）。
     *
     * @return 成功推送的设备数（不含已被 inactive 的设备）
     */
    public int pushToUser(Long userId, String title, String content, String deepLink) {
        requireUserCtx();
        if (userId == null) throw new ServiceException(400, "userId is required");
        if (title == null || title.isBlank()) throw new ServiceException(400, "title is required");
        if (content == null || content.isBlank()) throw new ServiceException(400, "content is required");

        List<MobPushToken> tokens = pushTokenService.getActiveTokens(userId);
        if (tokens.isEmpty()) {
            // 安全要求 #11
            throw new ServiceException(400, "No active push token for user " + userId);
        }

        int sent = 0;
        for (MobPushToken t : tokens) {
            // TODO P5 接 FCM / APNs / HMS Push SDK
            log.info("Push dispatched (stub) userId={} platform={} tokenHash={} title={} deepLink={}",
                userId, t.getPlatform(), t.getTokenHash(), title, deepLink);
            sent++;
        }
        return sent;
    }

    /**
     * 平台下全部 active token 广播。无 active token 返回 0（不算错误 — 平台可能没有装机用户）。
     */
    public int pushToAll(String platform, String title, String content) {
        requireUserCtx();
        if (platform == null || platform.isBlank()) {
            throw new ServiceException(400, "platform is required");
        }
        if (title == null || title.isBlank()) throw new ServiceException(400, "title is required");
        if (content == null || content.isBlank()) throw new ServiceException(400, "content is required");
        // TODO P5 接入推送 SDK 后，按 platform 分发
        log.info("Push broadcast (stub) platform={} title={} contentLen={}",
            platform, title, content.length());
        return 0;
    }

    private UserContext requireUserCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }
}
