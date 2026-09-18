package com.lumen.org;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.lumen.org", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.org.mapper")
public class OrgApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrgApplication.class, args);
    }
}