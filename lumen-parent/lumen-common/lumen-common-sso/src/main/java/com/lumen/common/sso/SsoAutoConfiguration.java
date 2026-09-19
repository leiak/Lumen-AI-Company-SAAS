package com.lumen.common.sso;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Auto-configuration for the {@code lumen-common-sso} module.
 *
 * <p>Scans {@code com.lumen.common.sso} for MyBatis mappers so that any service
 * pulling this module onto its classpath automatically picks up
 * {@link TicketMapper} without having to widen its own
 * {@code @MapperScan} base package (which is typically scoped to the
 * service-local package like {@code com.lumen.auth.mapper}).</p>
 */
@AutoConfiguration
@MapperScan("com.lumen.common.sso")
public class SsoAutoConfiguration {
}
