package com.lumen.gateway.filter;

import com.lumen.gateway.route.CanaryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Global filter that re-picks the upstream instance based on canary / gray metadata.
 *
 * <p>Runs AFTER {@link org.springframework.cloud.gateway.filter.LoadBalancerClientFilter}
 * (which sits at {@code LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150}). We read the
 * already-resolved {@code lb://serviceId} URI, fetch the instance list from Nacos via
 * {@link DiscoveryClient}, partition into gray (weight &gt; 0) and stable (weight == 0)
 * buckets, and rewrite the GATEWAY_REQUEST_URL_ATTR to point at the chosen host:port.</p>
 *
 * <p>Header contract:</p>
 * <ul>
 *   <li>{@code X-Canary: gray} — pin to gray bucket (falls back to stable if no gray instances)</li>
 *   <li>{@code X-Canary: stable} — pin to stable bucket</li>
 *   <li>no header — weighted random: probability of gray = sum(weight)/100</li>
 * </ul>
 *
 * <p>The filter also stamps {@code X-Canary-Gray: true|false} on the downstream request so
 * downstream services can observe which bucket was selected (useful for shadow logging and
 * verification during a rollout).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanaryWeightFilter implements GlobalFilter, Ordered {

    private final DiscoveryClient discoveryClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI originalUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (originalUri == null || !"lb".equals(originalUri.getScheme())) {
            // Not a load-balanced route — nothing to do. (e.g. ws://, http:// direct URIs.)
            return chain.filter(exchange);
        }

        String serviceId = originalUri.getHost();
        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances(serviceId);
        } catch (Exception ex) {
            // Nacos transient errors — let the LB chain continue and 503 if it can't find anything.
            log.warn("canary: discoveryClient.getInstances({}) failed: {}", serviceId, ex.toString());
            return chain.filter(exchange);
        }
        if (instances == null || instances.isEmpty()) {
            // No registered instances — pass through; LB filter will 503.
            return chain.filter(exchange);
        }

        String canaryHeader = exchange.getRequest().getHeaders().getFirst(CanaryConstants.HEADER_CANARY);
        ServiceInstance chosen = pickInstance(instances, canaryHeader);

        URI newUri = UriComponentsBuilder.fromUri(originalUri)
            .scheme("http")
            .host(chosen.getHost())
            .port(chosen.getPort())
            .build()
            .toUri();

        // Re-write the resolved URI for downstream filters / Netty routing.
        exchange.getAttributes().put(
            ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
            newUri
        );

        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header(CanaryConstants.HEADER_CANARY_GRAY, Boolean.toString(getCanaryWeight(chosen) > 0))
            .build();

        if (log.isDebugEnabled()) {
            log.debug("canary: serviceId={} header={} -> {}:{} gray={}",
                serviceId, canaryHeader, chosen.getHost(), chosen.getPort(),
                getCanaryWeight(chosen) > 0);
        }

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * Pick a single instance from the list, honoring the {@code X-Canary} header or, when
     * absent, distributing proportionally to instance weights.
     *
     * <p>Visibility is package-private for direct unit testing of the picker in isolation.</p>
     */
    ServiceInstance pickInstance(List<ServiceInstance> instances, String canaryHeader) {
        List<ServiceInstance> gray = new ArrayList<>();
        List<ServiceInstance> stable = new ArrayList<>();
        for (ServiceInstance i : instances) {
            if (getCanaryWeight(i) > 0) {
                gray.add(i);
            } else {
                stable.add(i);
            }
        }

        boolean wantGray;
        if (CanaryConstants.HEADER_VALUE_GRAY.equalsIgnoreCase(canaryHeader)) {
            wantGray = true;
        } else if (CanaryConstants.HEADER_VALUE_STABLE.equalsIgnoreCase(canaryHeader)) {
            wantGray = false;
        } else {
            wantGray = weightedRandomGray(instances);
        }

        List<ServiceInstance> pool;
        if (wantGray) {
            // Fall back to stable if no gray exists — never leave the user with a 503 just
            // because the operator requested gray during a 0% rollout window.
            pool = gray.isEmpty() ? stable : gray;
        } else {
            // If everything is gray (unusual) we still honor "stable" by picking from gray —
            // operators may have temporarily zeroed the stable instances during a cutover.
            pool = stable.isEmpty() ? gray : stable;
        }
        if (pool.isEmpty()) {
            // Both buckets empty — shouldn't happen because callers already guarded on isEmpty.
            return instances.get(0);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /**
     * Decide whether a request with no header should go to gray. Sum of gray weights is the
     * probability in percent (capped at 100, floored at 0). Stable instances get equal share
     * of the remaining probability implicitly because they're the only other option.
     */
    boolean weightedRandomGray(List<ServiceInstance> instances) {
        int total = 0;
        for (ServiceInstance i : instances) {
            total += clampWeight(getCanaryWeight(i));
        }
        if (total <= 0) {
            return false;
        }
        // total is bounded to 100 by clampWeight; if multiple instances are configured
        // higher we treat 100 as a hard cap (full canary).
        int cap = Math.min(total, 100);
        return ThreadLocalRandom.current().nextInt(100) < cap;
    }

    /**
     * Read the {@code canary.weight} metadata value as an int in [0, 100]. Missing or
     * non-numeric values default to 0 (stable). Values outside [0, 100] are clamped.
     */
    static int getCanaryWeight(ServiceInstance instance) {
        if (instance == null) {
            return 0;
        }
        String raw = instance.getMetadata().get(CanaryConstants.META_WEIGHT);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return clampWeight(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            log.debug("canary: invalid weight metadata '{}' on instance {}", raw, instance.getInstanceId());
            return 0;
        }
    }

    private static int clampWeight(int w) {
        if (w < 0) return 0;
        if (w > 100) return 100;
        return w;
    }

    @Override
    public int getOrder() {
        return CanaryConstants.FILTER_ORDER;
    }

    // Visible for tests that exercise the picker with an empty list directly.
    @SuppressWarnings("unused")
    static List<ServiceInstance> emptyInstances() {
        return Collections.emptyList();
    }
}