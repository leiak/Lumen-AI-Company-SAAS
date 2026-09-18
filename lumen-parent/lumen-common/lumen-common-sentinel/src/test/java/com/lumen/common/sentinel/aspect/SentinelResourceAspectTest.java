package com.lumen.common.sentinel.aspect;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.lumen.common.core.domain.R;
import com.lumen.common.sentinel.handler.LumenBlockHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for the {@code @SentinelResource} AOP interception path.
 *
 * <p>Uses {@link AspectJProxyFactory} (the same wiring Spring Boot uses under
 * the hood when it wraps {@code @SentinelResource}-annotated beans in a CGLIB
 * proxy) so we exercise the real {@link SentinelResourceAspect} advice against
 * a real {@code @SentinelResource}-annotated target method.</p>
 *
 * <p>The aspect calls {@code SphU.entry(resourceName, ...)} on every invocation;
 * configuring a {@link FlowRule} with {@code count=0.001} guarantees the
 * first invocation is blocked. The aspect must then resolve
 * {@link LumenBlockHandler#lumenBlock(int, int, String, BlockException)}
 * reflectively and return {@link R} with code 429.</p>
 *
 * <p>This is the AOP-level companion to {@code LumenBlockHandlerTest}, which
 * exercises the handler in isolation. The two together guarantee both the
 * handler signature and the actual reflection-driven dispatch work.</p>
 */
class SentinelResourceAspectTest {

    private ResourceHolder target;
    private ResourceHolder proxy;

    @BeforeEach
    void setUp() {
        // Configure a flow rule with count=0.001 so the very first call is
        // rejected — Sentinel validates count>0, so we use the smallest legal
        // positive value rather than 0.
        FlowRule rule = new FlowRule();
        rule.setResource("aspectTestList");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0.001);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        // Wire the SAME aspect Spring Boot registers via SentinelAutoConfiguration
        // around an actual @SentinelResource-annotated target. AspectJProxyFactory
        // is what Spring's @EnableAspectJAutoProxy uses internally for @Aspect
        // beans, so this is a faithful AOP test without needing a full context.
        target = new ResourceHolder();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SentinelResourceAspect());
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    void lumenBlockOverload_isInvokedByAspect_andReturnsCode429() {
        assertNotNull(proxy, "AspectJProxyFactory should produce a proxy");
        assertEquals(ResourceHolder.class, proxy.getClass().getSuperclass(),
                "proxy must extend ResourceHolder (CGLIB subclass proxy)");

        R<?> result = proxy.guardedList(1, 10, "kw");

        assertNotNull(result, "aspect should have intercepted and routed through lumenBlock");
        assertEquals(429, result.getCode(),
                "blocked invocation must yield the uniform 429 sentinel code; got: " + result);
    }

    /**
     * Holds the {@code @SentinelResource}-annotated method under test. The
     * method signature matches the {@code lumenBlock(int, int, String, BlockException)}
     * overload added to {@link LumenBlockHandler} — Sentinel's aspect picks
     * the (original args + trailing BlockException) variant reflectively.
     */
    public static class ResourceHolder {

        @SentinelResource(
                value = "aspectTestList",
                blockHandler = "lumenBlock",
                blockHandlerClass = LumenBlockHandler.class
        )
        public R<?> guardedList(int pageNum, int pageSize, String keyword) {
            // Should never execute — flow rule has count=0.001, every call blocked.
            return R.ok("should-not-reach-" + pageNum + ":" + pageSize + ":" + keyword);
        }
    }
}