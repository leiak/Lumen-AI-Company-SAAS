package com.lumen.common.security.config;

import com.lumen.common.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.lumen.common.security")
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfig {
}