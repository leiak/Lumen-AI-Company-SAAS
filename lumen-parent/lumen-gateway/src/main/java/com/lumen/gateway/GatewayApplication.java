package com.lumen.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Gateway is WebFlux; do NOT pick up lumen-common-web's servlet-only GlobalExceptionAdvice.
 * We exclude that package explicitly; a gateway-specific reactive advice is provided separately.
 */
@SpringBootApplication(scanBasePackages = {
    "com.lumen.gateway",
    "com.lumen.common.core",
    "com.lumen.common.security",
    "com.lumen.common.log",
    "com.lumen.common.mybatis",
    "com.lumen.common.redis"
}, scanBasePackageClasses = {})
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}