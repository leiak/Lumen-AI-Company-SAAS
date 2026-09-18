package com.lumen.common.security.filter;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads X-User-Id / X-Tenant-Id / X-User-Name / X-Session-Id headers
 * (injected by lumen-gateway's JwtAuthGlobalFilter) and populates UserContextHolder.
 * If no headers, leaves UserContextHolder unset (will be null in service).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class UserContextResolverFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String tenantId = request.getHeader("X-Tenant-Id");
        String userName = request.getHeader("X-User-Name");
        String sessionId = request.getHeader("X-Session-Id");

        if (userId != null && !userId.isBlank()) {
            try {
                Long uid = Long.parseLong(userId);
                Long tid = (tenantId == null || tenantId.isBlank()) ? null : Long.parseLong(tenantId);
                UserContext ctx = UserContext.builder()
                    .userId(uid)
                    .tenantId(tid)
                    .userName(userName)
                    .tokenId(sessionId)
                    .build();
                UserContextHolder.set(ctx);
            } catch (NumberFormatException ignored) {
                // Bad header — leave context null
            }
        }
        filterChain.doFilter(request, response);
        // Cleanup happens in UserContextCleanupFilter
    }
}