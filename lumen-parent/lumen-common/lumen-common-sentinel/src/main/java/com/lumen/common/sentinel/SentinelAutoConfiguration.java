package com.lumen.common.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration wiring Sentinel into Spring Boot 3.x services.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register the {@link SentinelResourceAspect} bean — required for
 *       {@code @SentinelResource} to actually intercept method calls.</li>
 *   <li>Load {@code rules.json} from the classpath (typically shipped by the
 *       downstream service) and push the parsed flow + degrade rules into
 *       {@link FlowRuleManager} / {@link DegradeRuleManager}.</li>
 * </ul>
 *
 * <p>The filter {@link com.lumen.common.sentinel.filter.SentinelWebFilter} is itself
 * a {@code @Component} guarded by SERVLET web-application detection, so this
 * configuration stays safe to include in WebFlux-only modules.</p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.csp.sentinel.SphU")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelAutoConfiguration {

    private static final String RULES_PATH = "sentinel/rules.json";

    @Bean
    @ConditionalOnMissingBean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void loadRules() {
        try {
            ClassPathResource resource = new ClassPathResource(RULES_PATH);
            if (!resource.exists()) {
                log.info("Sentinel rules.json not found on classpath; no flow/degrade rules loaded");
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, List<Map<String, Object>>> raw =
                        mapper.readValue(in, new TypeReference<Map<String, List<Map<String, Object>>>>() {});
                applyFlowRules(raw.get("flowRules"));
                applyDegradeRules(raw.get("degradeRules"));
            }
        } catch (Exception e) {
            // Don't kill the application — Sentinel can still run with programmatic rules.
            log.warn("Failed to load Sentinel rules.json: {}", e.getMessage(), e);
        }
    }

    private void applyFlowRules(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<FlowRule> parsed = mapper.convertValue(raw, new TypeReference<List<FlowRule>>() {});
            FlowRuleManager.loadRules(parsed);
            log.info("Loaded {} Sentinel flow rule(s)", parsed.size());
        } catch (Exception e) {
            log.warn("Failed to parse flow rules: {}", e.getMessage(), e);
        }
    }

    private void applyDegradeRules(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<DegradeRule> parsed = mapper.convertValue(raw, new TypeReference<List<DegradeRule>>() {});
            DegradeRuleManager.loadRules(parsed);
            log.info("Loaded {} Sentinel degrade rule(s)", parsed.size());
        } catch (Exception e) {
            log.warn("Failed to parse degrade rules: {}", e.getMessage(), e);
        }
    }
}