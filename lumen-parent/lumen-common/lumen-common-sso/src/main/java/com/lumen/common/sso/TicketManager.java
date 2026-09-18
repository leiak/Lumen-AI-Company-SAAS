package com.lumen.common.sso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * SSO 票据管理器：负责签发、消费和只读校验票据。
 * <p>
 * 票据本质是一个不透明的随机字符串（Base64 URL，无 padding）。所有元数据保存在
 * {@code sys_sso_ticket} 表中；消费采用单条原子 UPDATE 完成，杜绝"先查后改"的并发漏洞。
 * </p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * String ticket = ticketManager.issue(userId, tenantId, "lumen-admin", Duration.ofMinutes(5));
 * TicketPrincipal p = ticketManager.consume(ticket, "lumen-admin");
 * }</pre>
 */
@Slf4j
@Component
public class TicketManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TicketMapper ticketMapper;

    public TicketManager(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    /**
     * 签发票据并落库。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param appId    目标应用标识（消费时必须一致）
     * @param ttl      有效期；{@code null} 则使用 {@link TicketConstants#DEFAULT_TTL}
     * @return 票据字符串（Base64 URL，32 随机字节）
     */
    public String issue(Long userId, Long tenantId, String appId, Duration ttl) {
        validateUserId(userId);
        validateTenantId(tenantId);
        validateAppId(appId);

        Duration effectiveTtl = ttl == null ? TicketConstants.DEFAULT_TTL : ttl;
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new IllegalArgumentException("Ticket TTL must be positive, got " + effectiveTtl);
        }

        String ticket = generateTicket();
        LocalDateTime expiresAt = LocalDateTime.now().plus(effectiveTtl);

        SysSsoTicket row = new SysSsoTicket();
        row.setTicket(ticket);
        row.setUserId(userId);
        row.setTenantId(tenantId);
        row.setAppId(appId);
        row.setExpiresAt(expiresAt);
        row.setConsumedAt(null);
        ticketMapper.insert(row);

        log.info("SSO ticket issued: appId={}, userId={}, tenantId={}, expiresAt={}",
            appId, userId, tenantId, expiresAt);
        return ticket;
    }

    /**
     * 消费票据：原子地将 {@code consumed_at} 标记为 NOW()，并返回票据主体信息。
     * <p>
     * 消费成功后票据不可再次使用——任何对已消费/已过期/被逻辑删除票据的二次调用都返回 {@code null}。
     * </p>
     *
     * @param ticket 票据字符串
     * @param appId  应用标识（必须与签发时一致）
     * @return 票据主体；消费失败返回 {@code null}
     */
    public TicketPrincipal consume(String ticket, String appId) {
        validateTicket(ticket);
        validateAppId(appId);

        int updated = ticketMapper.consumeAtomically(ticket, appId);
        if (updated == 0) {
            log.warn("SSO ticket consume failed: ticket not found / expired / already consumed / appId mismatch");
            return null;
        }

        // 重新读取以拿到签发时的 userId/tenantId/expiresAt 等元数据。
        SysSsoTicket row = ticketMapper.selectOne(
            new LambdaQueryWrapper<SysSsoTicket>()
                .eq(SysSsoTicket::getTicket, ticket)
                .last("LIMIT 1")
        );
        if (row == null) {
            // 理论上刚刚消费成功、记录还在；落到这里说明并发删除了。
            log.warn("SSO ticket row vanished after atomic consume");
            return null;
        }

        return new TicketPrincipal(row.getUserId(), row.getTenantId(), row.getAppId(), row.getExpiresAt());
    }

    /**
     * 只读校验票据：检查票据是否存在、未消费、未过期且 appId 匹配。
     * <p>
     * 注意：该方法不会消费票据，仅用于诊断或心跳检查。
     * </p>
     *
     * @param ticket 票据字符串
     * @param appId  应用标识
     * @return true 表示票据仍处于有效状态
     */
    public boolean validate(String ticket, String appId) {
        if (ticket == null || ticket.isBlank() || appId == null || appId.isBlank()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        SysSsoTicket row = ticketMapper.selectOne(
            new LambdaQueryWrapper<SysSsoTicket>()
                .eq(SysSsoTicket::getTicket, ticket)
                .eq(SysSsoTicket::getAppId, appId)
                .eq(SysSsoTicket::getDeleted, 0)
                .last("LIMIT 1")
        );
        return row != null
            && row.getConsumedAt() == null
            && row.getExpiresAt() != null
            && row.getExpiresAt().isAfter(now);
    }

    // ---------------------------------------------------------------------
    // private helpers
    // ---------------------------------------------------------------------

    private static String generateTicket() {
        byte[] buf = new byte[TicketConstants.TICKET_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static void validateTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("Ticket must not be null or blank");
        }
    }

    private static void validateAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be null or blank");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive number, got " + userId);
        }
    }

    private static void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("tenantId must be non-negative, got " + tenantId);
        }
    }

    /**
     * 票据主体：消费成功时返回给调用方的不可变数据。
     * <p>
     * 字段语义与 {@link SysSsoTicket} 中对应字段一致；提供 record 风格的访问器以便
     * service 层直接构造 JWT 上下文。
     * </p>
     */
    public record TicketPrincipal(Long userId, Long tenantId, String appId, LocalDateTime expiresAt) {
    }
}