package com.lumen.gateway.route;

/**
 * Constants for canary / gray release routing.
 *
 * <p>Service instances publish Nacos metadata {@code canary.weight} (0–100) on each instance.
 * The <b>SUM</b> of {@code canary.weight} across all gray-tagged instances determines the
 * percentage of traffic that lands in the gray bucket; weights within the bucket do not
 * further distribute traffic (uniform random within the chosen bucket). Default if absent
 * = 0 (instance is treated as stable). Operators can pin a single request to gray or stable
 * via the {@code X-Canary} request header.</p>
 *
 * <p>This class is intentionally a static-constants holder (no Spring bean) so it can be
 * referenced by tests and other filters without bootstrapping Spring.</p>
 */
public final class CanaryConstants {

    /** Request header used to force gray or stable routing. Values: {@code gray} | {@code stable}. */
    public static final String HEADER_CANARY = "X-Canary";

    /** Downstream header indicating whether the picked instance was gray ({@code true} | {@code false}). */
    public static final String HEADER_CANARY_GRAY = "X-Canary-Gray";

    /**
     * Per-instance metadata controlling canary share. The SUM of {@code canary.weight} across
     * all gray-tagged instances determines the percentage of traffic that lands in the gray
     * bucket; weights within the bucket do not further distribute traffic (uniform random
     * within the bucket). Range: [0, 100]. Default if absent: 0 (instance is treated as stable).
     */
    public static final String META_WEIGHT = "canary.weight";

    /** {@code X-Canary: gray} — always pick a gray instance, falling back to stable if none exists. */
    public static final String HEADER_VALUE_GRAY = "gray";

    /** {@code X-Canary: stable} — always pick a stable instance. */
    public static final String HEADER_VALUE_STABLE = "stable";

    /**
     * Filter order for {@link com.lumen.gateway.filter.CanaryWeightFilter}. Must run AFTER
     * {@code LoadBalancerClientFilter} (order 10150) so that the {@code lb://} URI has already
     * been resolved into a concrete host:port. We deliberately replace the resolved URI to
     * redirect to a different instance based on canary weight.
     */
    public static final int FILTER_ORDER = 10160;

    private CanaryConstants() {
        // no instances
    }
}