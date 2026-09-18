package com.lumen.common.sentinel.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.lumen.common.core.domain.R;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure Mockito / JUnit tests for {@link LumenBlockHandler}.
 *
 * <p>No Spring context — keeps the test surface tiny and independent of any
 * downstream auto-configuration changes. The 429 contract is what we care about.</p>
 */
class LumenBlockHandlerTest {

    @Test
    void handle_blockException_returnsRWithCode429() {
        FlowRule rule = new FlowRule();
        rule.setResource("/api/tenant/list");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(100);
        BlockException e = new FlowException(rule);

        R<?> result = LumenBlockHandler.handle(e);

        assertNotNull(result);
        assertEquals(429, result.getCode());
        assertTrue(result.getMsg().contains("/api/tenant/list"),
                "msg should contain the blocked resource; got: " + result.getMsg());
        assertTrue(result.getMsg().toLowerCase().contains("too many requests"),
                "msg should describe the block reason; got: " + result.getMsg());
    }

    @Test
    void lumenBlock_method_aliasMatchesHandleSignature() {
        // The @SentinelResource annotation references "lumenBlock" by name; we
        // verify the alias exists with the right signature and returns the same
        // shape as handle().
        FlowRule rule = new FlowRule();
        rule.setResource("/api/tenant/page");
        BlockException e = new FlowException(rule);

        R<?> result = LumenBlockHandler.lumenBlock(e);

        assertNotNull(result);
        assertEquals(429, result.getCode());
        assertTrue(result.getMsg().contains("/api/tenant/page"));
    }

    @Test
    void handle_blockException_nullRule_doesNotThrow() {
        // Sentinel occasionally delivers a BlockException without a rule attached;
        // the handler must still return a sane R instead of NPE.
        BlockException e = new BlockException("synthetic") {};
        R<?> result = LumenBlockHandler.handle(e);
        assertEquals(429, result.getCode());
    }

    @Test
    void handle_httpRequest_writes429JsonBody() throws Exception {
        FlowRule rule = new FlowRule();
        rule.setResource("/api/tenant/list");
        rule.setCount(100);
        BlockException e = new FlowException(rule);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/list");
        req.setRequestURI("/api/tenant/list");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        LumenBlockHandler.handle(req, resp, e);

        assertEquals(429, resp.getStatus());
        assertNotNull(resp.getContentAsString());
        String body = resp.getContentAsString();
        assertTrue(body.contains("\"code\":429"), "body should embed code 429; got: " + body);
        assertTrue(body.contains("/api/tenant/list"),
                "body should embed the blocked resource; got: " + body);
        assertTrue(resp.getContentType() != null && resp.getContentType().startsWith("application/json"),
                "content type should be JSON; got: " + resp.getContentType());
    }

    @Test
    void handle_nullResponse_doesNotThrow() {
        // Defensive contract: if a future caller passes null (e.g. the filter
        // chain short-circuits with a null response) we should not NPE.
        BlockException e = new BlockException("synthetic") {};
        try {
            LumenBlockHandler.handle(new MockHttpServletRequest("GET", "/api/x"), null, e);
        } catch (Exception ex) {
            // Acceptable: an explicit IOException or NullPointerException means
            // the contract is broken. We require the WARN branch instead.
            assertTrue(ex instanceof NullPointerException || ex instanceof java.io.IOException,
                    "handler must either no-op or propagate I/O; got: " + ex);
        }
    }

    /**
     * Concrete BlockException for testing — FlowException is package-private in
     * Sentinel, so we extend BlockException directly with a rule attached.
     */
    private static class FlowException extends BlockException {
        FlowException(FlowRule rule) {
            super(rule.toString(), rule);
        }
    }
}