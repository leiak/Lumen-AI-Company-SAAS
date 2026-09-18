package com.lumen.common.sentinel.filter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.lumen.common.sentinel.handler.LumenBlockHandler;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter wiring Sentinel {@link SphU#entry(String)} on inbound requests.
 *
 * <p>Only requests under {@code /api/**} are gated — actuator, health, swagger, and
 * static resources are excluded so they can never accidentally trigger the limiter.
 * The {@code /api} prefix aligns with the gateway's routing contract: every public
 * request that reaches a service behind {@code lumen-gateway} has been forwarded under
 * {@code /api/**}.</p>
 *
 * <p>On {@link BlockException} the filter writes a 429 JSON body via
 * {@link LumenBlockHandler#handle(HttpServletRequest, HttpServletResponse, BlockException)}
 * and does NOT forward down the chain. {@link SphU#exit()} runs in a {@code finally}
 * block so the rule's sliding window stays accurate even on early returns.</p>
 *
 * <p>Activation is gated by {@link ConditionalOnWebApplication} SERVLET — WebFlux-only
 * services (e.g. an eventual {@code lumen-gateway} replacement) will simply skip
 * registration instead of crashing on missing servlet APIs.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelWebFilter implements Filter {

    private static final String API_PREFIX = "/api/";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String uri = req.getRequestURI();
        if (uri == null || !uri.startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // Use the path itself as the resource name so rules.json resources map 1:1
        // to the request paths (e.g. "/api/tenant/list").
        Entry entry = null;
        try {
            entry = SphU.entry(uri);
            chain.doFilter(request, response);
        } catch (BlockException e) {
            LumenBlockHandler.handle(req, resp, e);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
}