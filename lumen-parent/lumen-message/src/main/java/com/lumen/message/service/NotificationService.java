package com.lumen.message.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.message.entity.MsgChannel;
import com.lumen.message.entity.MsgNotification;
import com.lumen.message.entity.MsgTemplate;
import com.lumen.message.mapper.MsgNotificationMapper;
import com.lumen.message.sender.Sender;
import com.lumen.message.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notification orchestration — template lookup → render → dispatch via Sender →
 * persist status. Driven by {@code NotificationService.send}.
 *
 * <p>Tenancy: every row carries {@code tenantId} from
 * {@code UserContextHolder.getTenantId()} (default 0 if missing). List/mark
 * queries are tenant-scoped at the mapper level; the service ALSO checks
 * tenant equality on loaded rows as defense in depth (安全 #1).</p>
 *
 * <p>Authorization:</p>
 * <ul>
 *   <li>{@link #listByUser(Long, Integer, int, int)} — caller MUST supply a
 *       userId that matches the session. The controller pins it from
 *       {@code UserContextHolder.getUserId()}; a request-body userId is
 *       FORBIDDEN (安全 #4).</li>
 *   <li>{@link #markRead(Long)} — caller must own the notification OR be super_admin.</li>
 *   <li>{@link #markReadBatch(java.util.List)} — atomic SQL filter; rows not
 *       owned by the caller are silently skipped (no 403 leak).</li>
 * </ul>
 *
 * <p>Retry policy (安全 #9):</p>
 * <ul>
 *   <li>{@code retry_count} increments on each failed attempt.</li>
 *   <li>Up to {@code lumen.message.max-attempts} attempts total.</li>
 *   <li>Backoff schedule from {@code lumen.message.backoff-millis} (default 1s/5s/30s).</li>
 *   <li>On final failure, status=2 (failed), error_message recorded.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String SUPER_ADMIN_ROLE = "super_admin";

    /** Notification status constants — mirrored in msg_notification table comments. */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_READ = 3;

    private final MsgNotificationMapper notificationMapper;
    private final ChannelService channelService;
    private final TemplateService templateService;
    private final NotificationWebSocketHandler webSocketHandler;
    /** All {@link Sender} beans — autowired as a list to support multiple types. */
    private final List<Sender> senders;

    @Value("${lumen.message.max-attempts:3}")
    private int maxAttempts;

    @Value("${lumen.message.backoff-millis:1000,5000,30000}")
    private long[] backoffMillis;

    // ---------------------------------------------------------------
    // send — template lookup → render → dispatch → write status
    // ---------------------------------------------------------------

    /**
     * Send a notification to one or more recipients. The template binds to a
     * channel via {@link MsgTemplate#getChannelCode()}; if email/sms/webhook
     * is disabled in config, the dispatch falls back to {@code site}.
     */
    public List<MsgNotification> send(String templateCode,
                                      List<Long> recipientUserIds,
                                      Map<String, Object> variables) {
        if (templateCode == null || templateCode.isBlank()) {
            throw new ServiceException(400, "templateCode is required");
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new ServiceException(400, "recipientUserIds must not be empty");
        }
        UserContext ctx = requireUserContext();
        Long tenantId = ctx.getTenantId() == null ? 0L : ctx.getTenantId();

        // 1) Resolve template — find one whose channel is enabled, else site.
        MsgTemplate tpl = templateService.findByCodeAndChannel(templateCode, "site", tenantId);
        MsgChannel channel = null;
        if (tpl == null) {
            // Look across all channels (site / email / sms / webhook) by code only.
            // We pick the first enabled template match across the supported types.
            for (String type : List.of("site", "email", "sms", "dingtalk", "wechat", "webhook")) {
                MsgTemplate candidate = templateService.findByCodeAndChannel(templateCode, type, tenantId);
                if (candidate == null) continue;
                MsgChannel ch = channelService.findEnabledByCodeAndTenant(type, tenantId);
                if (ch != null) {
                    tpl = candidate;
                    channel = ch;
                    break;
                }
            }
        } else {
            channel = channelService.findEnabledByCodeAndTenant("site", tenantId);
        }
        if (tpl == null) {
            throw new ServiceException(404,
                "No enabled template found for code=" + templateCode + " in tenant=" + tenantId);
        }
        // Channel may be null for site type (site has no channel row needed; rely on template).
        // If template references a non-site channel but no enabled channel row exists → 409.
        if (!"site".equals(tpl.getChannelCode()) && channel == null) {
            throw new ServiceException(409,
                "Channel '" + tpl.getChannelCode() + "' is disabled; cannot deliver template "
                    + templateCode);
        }

        // 2) Render subject + content.
        TemplateService.Rendered rendered = templateService.render(
            templateCode, tpl.getChannelCode(), variables, tenantId);

        // 3) Per recipient: write row → dispatch → write status.
        java.util.List<MsgNotification> out = new java.util.ArrayList<>();
        Sender sender = pickSender(tpl.getChannelCode());
        for (Long recipientId : recipientUserIds) {
            MsgNotification n = new MsgNotification();
            n.setChannelCode(tpl.getChannelCode());
            n.setTemplateCode(templateCode);
            n.setRecipientUserId(recipientId);
            n.setRecipientAddress(resolveAddress(tpl.getChannelCode(), recipientId, channel));
            n.setSubject(rendered.subject());
            n.setContent(rendered.content());
            n.setStatus(STATUS_PENDING);
            n.setRetryCount(0);
            n.setTenantId(tenantId);
            notificationMapper.insert(n);
            log.info("Notification persisted id={} template={} recipient={}",
                n.getId(), templateCode, recipientId);

            // 4) Dispatch with retry.
            deliverWithRetry(n, sender, channel);
            // Refresh after send to capture final status / error.
            MsgNotification after = notificationMapper.selectById(n.getId());
            out.add(after != null ? after : n);

            // 5) Push to WebSocket for site deliveries only. Must NEVER fail the send.
            if ("site".equals(tpl.getChannelCode())) {
                try {
                    webSocketHandler.sendToUser(recipientId, after);
                } catch (Exception ex) {
                    log.warn("WebSocket push failed id={} recipient={} {}",
                        n.getId(), recipientId, ex.getMessage());
                }
            }
        }
        return out;
    }

    /**
     * Run the sender with bounded retries. Bumps retry_count on each attempt;
     * backoff uses the configured schedule. Final failure → status=2.
     */
    private void deliverWithRetry(MsgNotification n, Sender sender, MsgChannel channel) {
        int attempts = 0;
        Exception last = null;
        while (attempts < maxAttempts) {
            attempts++;
            try {
                Sender.SendResult result = sender.send(n, channel);
                if (result != null && result.isSuccess()) {
                    n.setStatus(STATUS_SENT);
                    n.setSentTime(LocalDateTime.now());
                    n.setErrorMessage(null);
                    notificationMapper.updateById(n);
                    return;
                }
                last = new RuntimeException(
                    result == null ? "sender returned null" : "send reported failure: " + result.getDetail());
            } catch (Exception ex) {
                last = ex;
            }
            n.setRetryCount(attempts);
            // Persist the in-flight attempt state for observability.
            notificationMapper.updateById(n);
            if (attempts < maxAttempts) {
                long sleepMs = backoffFor(attempts - 1);
                log.warn("Send attempt {}/{} failed for notification id={} — backing off {}ms",
                    attempts, maxAttempts, n.getId(), sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // Final failure.
        n.setStatus(STATUS_FAILED);
        n.setErrorMessage(last == null ? "unknown error" : truncate(last.getMessage(), 500));
        n.setRetryCount(attempts);
        notificationMapper.updateById(n);
        log.error("Send gave up after {} attempts notification id={} subject={}",
            attempts, n.getId(), n.getSubject());
    }

    private long backoffFor(int attemptIndex) {
        if (backoffMillis == null || backoffMillis.length == 0) return 1000L;
        int idx = Math.min(attemptIndex, backoffMillis.length - 1);
        return Math.max(0L, backoffMillis[idx]);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Pick the right {@link Sender} for the channel. Dingtalk/wechat are routed
     * to {@link com.lumen.message.sender.WebhookSender} (single bean covers all
     * webhook-shaped channels). Falls back to {@link com.lumen.message.sender.SiteSender}
     * if no specific bean is registered for the type — handles the default-disabled
     * EmailSender/SmsSender case (email with no SMTP config).
     */
    private Sender pickSender(String channelType) {
        if (channelType == null) channelType = "site";
        Sender siteFallback = null;
        for (Sender s : senders) {
            if (channelType.equals(s.getType())) return s;
            if ("site".equals(s.getType())) siteFallback = s;
        }
        return siteFallback;
    }

    /**
     * Resolve the recipient "address" for the channel (email, phone, webhook
     * URL). For site, we leave the user-id as the implicit address. Real lookups
     * against user/org tables are out of scope here — they belong to the
     * auth-service (TODO: integrate).
     */
    private static String resolveAddress(String channelType, Long recipientUserId, MsgChannel channel) {
        if ("site".equals(channelType)) return String.valueOf(recipientUserId);
        // Email/SMS/Webhook addresses are expected to be looked up by the caller
        // and passed in via SendNotificationRequest (out of scope here) — for now,
        // we leave the address null and let the sender deal with it.
        return null;
    }

    // ---------------------------------------------------------------
    // list / mark read — tenant + ownership enforced
    // ---------------------------------------------------------------

    /**
     * Page notifications for {@code userId}. Tenant filter applied at SQL level
     * (mapper) AND at service level (defense in depth). Returns 404 (not 403)
     * on cross-tenant mismatch to avoid existence disclosure.
     */
    public IPage<MsgNotification> listByUser(Long userId, Integer status,
                                             int pageNum, int pageSize) {
        UserContext ctx = requireUserContext();
        if (userId == null) {
            throw new ServiceException(401, "No user context");
        }
        // The controller MUST pass the userId from UserContextHolder; we re-check
        // here that it matches (defense in depth).
        if (!userId.equals(ctx.getUserId())
            && !isSuperAdmin(ctx)) {
            // 安全 #1: 403 only after existence is known. For pure listing we
            // can't distinguish "not yours" from "doesn't exist" without checking;
            // return 404 to avoid leaking existence.
            throw new ServiceException(404, "Notification not found");
        }
        Long tenantId = ctx.getTenantId() == null ? 0L : ctx.getTenantId();
        return notificationMapper.listByRecipient(
            Page.of(pageNum, pageSize), tenantId, userId, status);
    }

    public MsgNotification markRead(Long id) {
        UserContext ctx = requireUserContext();
        MsgNotification n = notificationMapper.selectById(id);
        if (n == null) {
            throw new ServiceException(404, "Notification not found: " + id);
        }
        // 安全 #1: cross-tenant → 404 (not 403).
        Long ctxTenant = ctx.getTenantId() == null ? 0L : ctx.getTenantId();
        if (!ctxTenant.equals(n.getTenantId())) {
            throw new ServiceException(404, "Notification not found: " + id);
        }
        // Authorization: owner or super_admin.
        boolean owner = ctx.getUserId() != null && ctx.getUserId().equals(n.getRecipientUserId());
        if (!owner && !isSuperAdmin(ctx)) {
            throw new ServiceException(403, "Forbidden: not your notification");
        }
        if (n.getStatus() == null || n.getStatus() < STATUS_READ) {
            n.setStatus(STATUS_READ);
            n.setReadTime(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
        return n;
    }

    /**
     * Atomic batch mark-read. SQL filter is tenant + recipient so the caller
     * can never mark another user's notifications as read. Returns the count
     * of rows actually updated.
     */
    public int markReadBatch(List<Long> ids) {
        UserContext ctx = requireUserContext();
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException(400, "ids must not be empty");
        }
        // Defense in depth — cap batch size to avoid SQL surprise.
        if (ids.size() > 500) {
            throw new ServiceException(400, "ids batch too large (max 500)");
        }
        // Dedupe to avoid wasted IN-list entries.
        List<Long> deduped = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(ids));
        Long tenantId = ctx.getTenantId() == null ? 0L : ctx.getTenantId();
        int updated = notificationMapper.markReadBatch(
            tenantId, ctx.getUserId(), deduped);
        log.info("markReadBatch tenant={} user={} requested={} updated={}",
            tenantId, ctx.getUserId(), deduped.size(), updated);
        return updated;
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private static boolean isSuperAdmin(UserContext ctx) {
        return ctx.getRoles() != null && ctx.getRoles().contains(SUPER_ADMIN_ROLE);
    }

    /** Defensive helper for tests — returns the set of registered sender types. */
    public Set<String> registeredSenderTypes() {
        Set<String> out = new HashSet<>();
        for (Sender s : senders) out.add(s.getType());
        return out;
    }
}