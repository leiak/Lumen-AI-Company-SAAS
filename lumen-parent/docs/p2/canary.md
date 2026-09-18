# Canary / Gray Routing Runbook

Status: implemented in `feat(gateway): Nacos-weighted canary routing via X-Canary header`.

The gateway rewrites the upstream instance for any `lb://` route based on the `canary.weight`
metadata each instance publishes to Nacos. No code change is needed to start, ramp, or finish
a canary rollout — operators control traffic purely via Nacos metadata and request headers.

---

## 1. Convention

| Concept                  | How it's expressed                                                                 |
| ------------------------ | ---------------------------------------------------------------------------------- |
| A "gray" instance        | Has Nacos instance metadata `canary.weight` with an integer value in `(0, 100]`    |
| A "stable" instance      | Has `canary.weight` = 0, or no `canary.weight` metadata at all (default = stable) |
| Pin to gray              | Request header `X-Canary: gray`                                                    |
| Pin to stable            | Request header `X-Canary: stable`                                                  |
| Default (no header)      | Weighted random — probability of gray = `sum(weights across gray instances) / 100` |
| Downstream observability | Request header `X-Canary-Gray: true` (or `false`) is set on the upstream request   |

Weights are integer percentages. Values outside `[0, 100]` are clamped. Non-numeric values
are treated as `0` (stable).

---

## 2. Publishing a Gray Instance

In the Nacos console, on the target service's instance detail page:

1. Edit metadata
2. Add key `canary.weight` with value `N` (e.g. `10` for 10% canary)
3. Save. The gateway picks up the new metadata within its discovery refresh window
   (default 30s).

Example — 10% canary on `auth-service`:
```
instance metadata:
  canary.weight = 10
```

The other instances of `auth-service` (without `canary.weight` metadata, or with
`canary.weight=0`) are the stable bucket.

---

## 3. Rolling Forward / Backward

Forward (increase canary share):
```
canary.weight = 10   # observe metrics, error rates
canary.weight = 25
canary.weight = 50
canary.weight = 100  # full canary — gray is now the production behavior
```

Backward (rollback):
```
canary.weight = 0    # instance falls back to stable bucket; no code change
```
Or, in an emergency, deregister the instance entirely from Nacos.

A single instance can move between buckets instantly by editing the metadata — there's no
redeploy required.

---

## 4. Observing Canary Traffic

### Per-request

Downstream services can branch on `X-Canary-Gray`. The header is set by the gateway for every
request that was matched to an `lb://` route.

```java
String grayHeader = request.getHeader("X-Canary-Gray");
if ("true".equals(grayHeader)) {
    // gray-only behavior — e.g. shadow logging, dual-write to a new schema
}
```

### From the client side

Clients (internal services, synthetic monitors) can pin themselves to one bucket with
`X-Canary`:
```
GET /auth/profile
X-Canary: gray       # always hits a gray auth-service instance
X-Canary: stable     # always hits a stable auth-service instance
```

### Gateway logs

The canary filter logs at INFO level on first hit and DEBUG on subsequent hits:
```
canary: serviceId=auth-service header=gray -> 10.0.0.7:9201 gray=true
```

For a permanent log line per request, set:
```
logging.level.com.lumen.gateway.filter.CanaryWeightFilter=DEBUG
```

---

## 5. Debugging

### Symptom: gray traffic isn't reaching the canary instance

1. Confirm the instance has the metadata: Nacos console → Instance detail → Metadata.
2. Confirm the gateway sees it:
   ```
   curl http://localhost:9200/actuator/...
   ```
   (or inspect `discoveryClient.getInstances(serviceId)` from a debug endpoint).
3. Check the gateway logs for `canary: ...`. If you see only stable picks, the request
   probably carried `X-Canary: stable` or the gray instance is being excluded by the LB
   client (e.g. it returned an unhealthy status).
4. Force a gray pick: `curl -H 'X-Canary: gray' http://localhost:9200/auth/profile`.

### Symptom: weighted distribution is off

The filter uses `ThreadLocalRandom`. Expected accuracy is ±5% over a few thousand trials.
If you see a wildly skewed ratio:
- Verify each gray instance's `canary.weight` parses as an integer.
- Verify the value is `0 < N <= 100`.
- Verify you're sending enough requests — single-digit samples are noisy.

### Symptom: requests get 503 "no available instances"

The canary filter only fires when `getInstances(serviceId)` returns a non-empty list. If
Nacos returns nothing, the filter passes through and the LB filter returns 503. Check:
- Service name in the route matches the registered Nacos service id.
- Nacos namespace (`lumen-public`) and credentials are correct.
- Instance is `UP` (Nacos will not return `OUT_OF_SERVICE` instances to the LB client).

---

## 6. Rolling Out Canary

Recommended steps for a new version:

1. Deploy new instance(s) with version label but **without** `canary.weight` metadata.
2. Verify they're UP in Nacos.
3. Set `canary.weight=5` on one canary instance. Watch error rate, latency, business KPIs.
4. Step weight: 5 → 10 → 25 → 50 → 100. Hold at each step until metrics stabilize.
5. Once `canary.weight=100`, you've achieved a full cutover. Remove or scale down old
   stable instances, and remove the `canary.weight` metadata from the now-stable gray
   instance (or set it back to `0`).

---

## 7. Reference

- Filter: `lumen-gateway/src/main/java/com/lumen/gateway/filter/CanaryWeightFilter.java`
- Constants: `lumen-gateway/src/main/java/com/lumen/gateway/route/CanaryConstants.java`
- Tests: `lumen-gateway/src/test/java/com/lumen/gateway/filter/CanaryWeightFilterTest.java`
- Config: `lumen-gateway/src/main/resources/application.yml`