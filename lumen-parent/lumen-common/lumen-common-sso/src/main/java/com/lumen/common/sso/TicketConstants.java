package com.lumen.common.sso;

import java.time.Duration;

/**
 * SSO 票据相关常量。
 * <p>
 * - TICKET_RANDOM_BYTES: 票据随机字节长度（32 字节 → Base64 URL 编码约 43 字符）。
 * - DEFAULT_TTL: 默认票据有效期，5 分钟。
 * - DEFAULT_APP_ID: 默认应用标识，当调用方未传 appId 时回退到该值。
 * </p>
 */
public final class TicketConstants {

    /** 票据随机字节数（32 字节足够抵御暴力枚举）。 */
    public static final int TICKET_RANDOM_BYTES = 32;

    /** 默认票据有效期。 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** 默认应用标识，便于在单应用测试场景下省略 appId。 */
    public static final String DEFAULT_APP_ID = "lumen-default";

    private TicketConstants() {
        // utility class
    }
}