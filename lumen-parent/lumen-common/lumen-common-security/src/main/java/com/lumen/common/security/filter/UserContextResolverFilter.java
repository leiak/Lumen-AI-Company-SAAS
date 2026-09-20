package com.lumen.common.security.filter;

import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads X-User-Id / X-Tenant-Id / X-User-Name / X-Session-Id headers
 * (injected by lumen-gateway's JwtAuthGlobalFilter) and populates UserContextHolder.
 * If no headers, leaves UserContextHolder unset (will be null in service).
 *
 * <p>Also reads {@code X-Roles} (comma-separated role keys) and seeds a Spring Security
 * {@code Authentication} on the {@code SecurityContextHolder} with corresponding
 * {@code ROLE_xxx} authorities. This is what makes
 * {@code @PreAuthorize("hasRole('super_admin')")} actually match — without it,
 * every controller annotated with a role check returns 500 (AccessDeniedException
 * wrapped by GlobalExceptionAdvice).</p>
 *
 * <p><b>Critical:</b> We persist the SecurityContext via {@link SecurityContextRepository}
 * (not just {@code SecurityContextHolder.setContext(...)}) because Spring Security 6's
 * {@code SecurityContextHolderFilter} runs after this filter and would otherwise clear
 * what we set on the ThreadLocal. By saving through the repository, the next
 * SecurityContextHolderFilter.loadContext(...) reads our Authentication back. Also we
 * directly call {@code setDeferredContext} so the current request thread sees it
 * immediately for downstream AOP / @PreAuthorize checks.</p>
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class UserContextResolverFilter extends OncePerRequestFilter {

    private final SecurityContextRepository securityContextRepository;

    @Autowired
    public UserContextResolverFilter(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository != null
            ? securityContextRepository
            : new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String tenantId = request.getHeader("X-Tenant-Id");
        String userName = request.getHeader("X-User-Name");
        String sessionId = request.getHeader("X-Session-Id");
        String rolesHeader = request.getHeader("X-Roles");

        log.debug("[UCRF] uri={} X-User-Id={} X-Roles={}",
            request.getRequestURI(), userId, rolesHeader);

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

                // Seed Spring Security context with role authorities so @PreAuthorize
                // hasRole(...) checks succeed downstream of the gateway.
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (rolesHeader != null && !rolesHeader.isBlank()) {
                    for (String r : rolesHeader.split(",")) {
                        String trimmed = r.trim();
                        if (!trimmed.isEmpty()) authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
                    }
                }
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(uid, null, authorities);
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(auth);
                // Set on the current thread so AOP / @PreAuthorize sees it immediately.
                SecurityContextHolder.setContext(securityContext);
                // Persist via repository so Spring Security's SecurityContextHolderFilter
                // (which runs after us) loads it back instead of clobbering with anonymous.
                securityContextRepository.saveContext(securityContext, request, response);
                log.debug("[UCRF] seeded auth with {} authorities: {}",
                    authorities.size(), authorities);
            } catch (NumberFormatException ignored) {
                // Bad header — leave context null
            }
        }
        filterChain.doFilter(request, response);
        // Cleanup happens in UserContextCleanupFilter
    }
}