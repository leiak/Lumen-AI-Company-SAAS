package com.lumen.common.sentinel.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.lumen.common.core.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Default Sentinel block handler for the Lumen platform.
 *
 * <p>Two overloads:
 * <ul>
 *   <li>{@link #handle(BlockException)} — used by {@code @SentinelResource} when a
 *       method-level resource is blocked. Returns an {@link R} with HTTP-style code 429
 *       so controllers / {@code R.ok(...)} callers get a uniform failure shape.</li>
 *   <li>{@link #handle(HttpServletRequest, HttpServletResponse, BlockException)} —
 *       used by {@link com.lumen.common.sentinel.filter.SentinelWebFilter} when a
 *       request URI is blocked. Writes the same payload as JSON directly to the
 *       servlet response (status 429) and short-circuits the filter chain.</li>
 * </ul>
 *
 * <p>The blocked resource name and rule type are included in the message and logged
 * at WARN so traffic spikes / misconfigured limits are easy to spot in production.</p>
 */
@Slf4j
public final class LumenBlockHandler {

    /** HTTP-style code used in {@link R} payloads for blocked requests. */
    public static final int BLOCK_CODE = 429;

    private LumenBlockHandler() {
    }

    /**
     * Resource-level block handler for {@code @SentinelResource(blockHandler = "lumenBlock", blockHandlerClass = LumenBlockHandler.class)}.
     *
     * <p>Static + parameter type matches what Sentinel's annotation aspect expects.
     * This overload mirrors the original method signature exactly so Sentinel's
     * AOP advice (which prefers "original args + trailing BlockException") can
     * locate it reflectively on {@code @SentinelResource}-annotated methods.</p>
     */
    public static R<?> lumenBlock(int pageNum, int pageSize, String keyword, BlockException e) {
        return handle(e);
    }

    /**
     * Single-argument fallback for {@code @SentinelResource}-annotated methods
     * that don't carry arguments (or where Sentinel's 1.8.x version only looks
     * up a no-arg / single-arg handler). Kept so older Sentinel fallbacks still
     * resolve; the AOP advice picks the {@code (..., BlockException)} variant
     * first when both are present.
     */
    public static R<?> lumenBlock(BlockException e) {
        return handle(e);
    }

    /**
     * Resource-level block handler returning an {@link R} payload.
     */
    public static R<?> handle(BlockException e) {
        String resource = resolveResource(e);
        String ruleType = resolveRuleType(e);
        log.warn("Sentinel block: resource='{}', ruleType='{}', reason='{}'",
                resource, ruleType, e == null ? "null" : e.getMessage());
        return R.fail(BLOCK_CODE, "Too many requests: " + resource);
    }

    /**
     * Servlet-level block handler used by the web filter. Writes JSON directly to
     * the response, sets status 429, and returns normally so the filter chain stops.
     */
    public static void handle(HttpServletRequest req, HttpServletResponse resp, BlockException e) throws IOException {
        if (resp == null) {
            log.warn("Sentinel block on null HttpServletResponse for path={}", safePath(req));
            return;
        }
        String resource = resolveResource(e);
        String ruleType = resolveRuleType(e);
        String path = safePath(req);
        log.warn("Sentinel block: path='{}', resource='{}', ruleType='{}', reason='{}'",
                path, resource, ruleType, e == null ? "null" : e.getMessage());

        R<?> body = R.fail(BLOCK_CODE, "Too many requests: " + resource);
        resp.setStatus(BLOCK_CODE);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = resp.getWriter()) {
            writer.write(toJson(body));
            writer.flush();
        }
    }

    private static String resolveResource(BlockException e) {
        if (e == null || e.getRule() == null) {
            return "unknown";
        }
        return e.getRule().getResource();
    }

    private static String resolveRuleType(BlockException e) {
        if (e == null || e.getRule() == null) {
            return "unknown";
        }
        // BlockException carries a rule whose type disambiguates flow vs degrade etc.
        if (e.getRule() instanceof com.alibaba.csp.sentinel.slots.block.flow.FlowRule) {
            return "flow";
        }
        if (e.getRule() instanceof com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule) {
            return "degrade";
        }
        return e.getRule().getClass().getSimpleName();
    }

    private static String safePath(HttpServletRequest req) {
        return req == null ? "<null-request>" : (req.getRequestURI() == null ? "" : req.getRequestURI());
    }

    /**
     * Hand-rolled JSON encoder — avoids pulling Jackson into a {@code common-sentinel}
     * dependency graph. The {@link R} shape is small and stable.
     */
    private static String toJson(R<?> r) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"code\":").append(r.getCode())
                .append(",\"msg\":").append(jsonString(r.getMsg()))
                .append(",\"data\":null}");
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}