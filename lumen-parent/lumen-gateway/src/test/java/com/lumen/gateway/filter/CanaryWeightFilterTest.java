package com.lumen.gateway.filter;

import com.lumen.gateway.route.CanaryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CanaryWeightFilter}. Pure Mockito — no live Nacos.
 *
 * <p>The exchange is constructed manually: we set the {@code GATEWAY_REQUEST_URL_ATTR}
 * (which is normally populated by the predicate-matching phase) and verify the filter
 * rewrites it to a concrete http://host:port URI.</p>
 */
class CanaryWeightFilterTest {

    private DiscoveryClient discoveryClient;
    private GatewayFilterChain chain;
    private CanaryWeightFilter filter;

    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        filter = new CanaryWeightFilter(discoveryClient);
    }

    // -------------------------------------------------------------- helpers

    private static ServiceInstance instance(String id, String host, int port, Integer canaryWeight) {
        Map<String, String> meta = new HashMap<>();
        if (canaryWeight != null) {
            meta.put(CanaryConstants.META_WEIGHT, canaryWeight.toString());
        }
        DefaultServiceInstance si = new DefaultServiceInstance(id, "test-service", host, port, false, meta);
        return si;
    }

    private ServerWebExchange exchangeWithUri(URI uri) {
        return exchangeWithUri(uri, null);
    }

    private ServerWebExchange exchangeWithUri(URI uri, String canaryHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
            .method(org.springframework.http.HttpMethod.GET, URI.create("/"))
            .header(HttpHeaders.HOST, "localhost:9200");
        if (canaryHeader != null) {
            builder.header(CanaryConstants.HEADER_CANARY, canaryHeader);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, uri);
        return exchange;
    }

    // -------------------------------------------------------------- tests

    @Test
    @DisplayName("X-Canary: gray -> always picks a gray instance (when any exist)")
    void headerGrayAlwaysPicksGray() {
        ServiceInstance stable1 = instance("s1", "10.0.0.1", 9001, 0);
        ServiceInstance stable2 = instance("s2", "10.0.0.2", 9001, 0);
        ServiceInstance gray = instance("g1", "10.0.0.3", 9001, 50);
        when(discoveryClient.getInstances("auth-service"))
            .thenReturn(List.of(stable1, stable2, gray));

        URI lbUri = URI.create("lb://auth-service");
        for (int i = 0; i < 50; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri, "gray");
            filter.filter(ex, chain).block();

            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved).isNotNull();
            assertThat(resolved.getScheme()).isEqualTo("http");
            assertThat(resolved.getHost()).isEqualTo("10.0.0.3");
            assertThat(resolved.getPort()).isEqualTo(9001);
        }
    }

    @Test
    @DisplayName("X-Canary: stable -> always picks a stable instance")
    void headerStableAlwaysPicksStable() {
        ServiceInstance stable1 = instance("s1", "10.0.0.1", 9001, 0);
        ServiceInstance stable2 = instance("s2", "10.0.0.2", 9001, 0);
        ServiceInstance gray = instance("g1", "10.0.0.3", 9001, 50);
        when(discoveryClient.getInstances("auth-service"))
            .thenReturn(List.of(stable1, stable2, gray));

        URI lbUri = URI.create("lb://auth-service");
        for (int i = 0; i < 50; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri, "stable");
            filter.filter(ex, chain).block();

            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved.getHost()).isIn("10.0.0.1", "10.0.0.2");
        }
    }

    @Test
    @DisplayName("no header, no gray instances -> picks stable")
    void noHeaderNoGrayPicksStable() {
        ServiceInstance s1 = instance("s1", "10.0.0.1", 9001, 0);
        ServiceInstance s2 = instance("s2", "10.0.0.2", 9001, null);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(s1, s2));

        URI lbUri = URI.create("lb://svc");
        for (int i = 0; i < 20; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri);
            filter.filter(ex, chain).block();
            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved.getHost()).isIn("10.0.0.1", "10.0.0.2");
        }
    }

    @Test
    @DisplayName("no header, only gray instances -> picks gray")
    void noHeaderOnlyGrayPicksGray() {
        ServiceInstance g1 = instance("g1", "10.0.0.1", 9001, 30);
        ServiceInstance g2 = instance("g2", "10.0.0.2", 9001, 80);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(g1, g2));

        URI lbUri = URI.create("lb://svc");
        for (int i = 0; i < 20; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri);
            filter.filter(ex, chain).block();
            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved.getHost()).isIn("10.0.0.1", "10.0.0.2");
        }
    }

    @Test
    @DisplayName("mixed instances, no header -> weighted ratio within +/- 10% of expected")
    void weightedRatioRespected() {
        // total gray weight = 25 (one gray instance with weight 25), 1 stable instance.
        // Expected gray ratio = 25%, stable ratio = 75%.
        ServiceInstance stable = instance("s1", "10.0.0.1", 9001, 0);
        ServiceInstance gray = instance("g1", "10.0.0.2", 9001, 25);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(stable, gray));

        int trials = 10_000;
        int grayCount = 0;
        int stableCount = 0;
        for (int i = 0; i < trials; i++) {
            URI lbUri = URI.create("lb://svc");
            ServerWebExchange ex = exchangeWithUri(lbUri);
            filter.filter(ex, chain).block();
            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            if ("10.0.0.2".equals(resolved.getHost())) {
                grayCount++;
            } else {
                stableCount++;
            }
        }
        double grayRatio = grayCount / (double) trials;
        // Expect ~0.25, allow +/- 0.10 (loose because the test must be stable across runs).
        assertThat(grayRatio).isBetween(0.15, 0.35);
        assertThat(grayCount + stableCount).isEqualTo(trials);
    }

    @Test
    @DisplayName("non-lb URI scheme passes through unchanged")
    void nonLbSchemePassesThrough() {
        // Simulate a route that resolves to http:// directly (e.g. a static route).
        URI direct = URI.create("http://upstream.example.com:8080/path");
        ServerWebExchange ex = exchangeWithUri(direct);

        filter.filter(ex, chain).block();

        // URI attribute must NOT be replaced by the canary filter.
        URI after = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertThat(after).isEqualTo(direct);

        // Downstream header should not be set on non-lb requests either.
        ServerHttpRequest req = ex.getRequest();
        assertThat(req.getHeaders().getFirst(CanaryConstants.HEADER_CANARY_GRAY)).isNull();

        verify(chain).filter(ex);
    }

    @Test
    @DisplayName("empty instance list falls through without NPE")
    void emptyInstanceListFallsThrough() {
        when(discoveryClient.getInstances("svc")).thenReturn(List.of());
        URI lbUri = URI.create("lb://svc");
        ServerWebExchange ex = exchangeWithUri(lbUri);

        // Should not throw.
        filter.filter(ex, chain).block();

        // URI is left untouched so the downstream LB filter can 503 cleanly.
        URI after = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertThat(after).isEqualTo(lbUri);
    }

    @Test
    @DisplayName("canary.weight=0 is treated as stable even when header=gray picks from stable")
    void zeroWeightIsStable() {
        ServiceInstance zeroWeight = instance("z1", "10.0.0.1", 9001, 0);
        ServiceInstance realGray = instance("g1", "10.0.0.2", 9001, 50);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(zeroWeight, realGray));

        URI lbUri = URI.create("lb://svc");
        // X-Canary: gray must NEVER pick 10.0.0.1 because that instance's weight is 0.
        for (int i = 0; i < 50; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri, "gray");
            filter.filter(ex, chain).block();
            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved.getHost()).isEqualTo("10.0.0.2");
        }
    }

    @Test
    @DisplayName("invalid weight metadata is treated as 0 (stable)")
    void invalidWeightTreatedAsZero() {
        Map<String, String> meta = new HashMap<>();
        meta.put(CanaryConstants.META_WEIGHT, "not-a-number");
        ServiceInstance bad = new DefaultServiceInstance("b1", "svc", "10.0.0.1", 9001, false, meta);

        ServiceInstance realGray = instance("g1", "10.0.0.2", 9001, 50);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(bad, realGray));

        URI lbUri = URI.create("lb://svc");
        for (int i = 0; i < 20; i++) {
            ServerWebExchange ex = exchangeWithUri(lbUri, "gray");
            filter.filter(ex, chain).block();
            URI resolved = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            assertThat(resolved.getHost()).isEqualTo("10.0.0.2");
        }
    }

    @Test
    @DisplayName("X-Canary-Gray header is set on the downstream request")
    void canaryGrayHeaderSetDownstream() {
        ServiceInstance gray = instance("g1", "10.0.0.3", 9001, 50);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(gray));

        URI lbUri = URI.create("lb://svc");
        ServerWebExchange ex = exchangeWithUri(lbUri);
        // Capture the exchange the chain receives so we can inspect the mutated request.
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(ex, chain).block();
        verify(chain).filter(captor.capture());

        ServerHttpRequest downstreamReq = captor.getValue().getRequest();
        assertThat(downstreamReq.getHeaders().getFirst(CanaryConstants.HEADER_CANARY_GRAY)).isEqualTo("true");
    }

    @Test
    @DisplayName("X-Canary-Gray header is 'false' for stable instance")
    void canaryGrayHeaderFalseForStable() {
        ServiceInstance stable = instance("s1", "10.0.0.1", 9001, 0);
        when(discoveryClient.getInstances("svc")).thenReturn(List.of(stable));

        URI lbUri = URI.create("lb://svc");
        ServerWebExchange ex = exchangeWithUri(lbUri);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        filter.filter(ex, chain).block();
        verify(chain).filter(captor.capture());

        assertThat(captor.getValue().getRequest().getHeaders().getFirst(CanaryConstants.HEADER_CANARY_GRAY))
            .isEqualTo("false");
    }

    // -------------------------------------------------------------- tiny ServiceInstance stub

    /**
     * Minimal ServiceInstance implementation for tests — Spring Cloud's default impl
     * requires a builder we don't want to drag into a unit test.
     */
    private static final class DefaultServiceInstance implements ServiceInstance {
        private final String instanceId;
        private final String serviceId;
        private final String host;
        private final int port;
        private final boolean secure;
        private final Map<String, String> metadata;

        DefaultServiceInstance(String instanceId, String serviceId, String host, int port,
                              boolean secure, Map<String, String> metadata) {
            this.instanceId = instanceId;
            this.serviceId = serviceId;
            this.host = host;
            this.port = port;
            this.secure = secure;
            this.metadata = metadata == null ? new HashMap<>() : metadata;
        }

        @Override public String getInstanceId() { return instanceId; }
        @Override public String getServiceId() { return serviceId; }
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
        @Override public boolean isSecure() { return secure; }
        @Override public URI getUri() { return URI.create((secure ? "https" : "http") + "://" + host + ":" + port); }
        @Override public Map<String, String> getMetadata() { return metadata; }
        @Override public String getScheme() { return secure ? "https" : "http"; }
    }
}