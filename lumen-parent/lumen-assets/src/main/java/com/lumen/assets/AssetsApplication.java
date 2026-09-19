package com.lumen.assets;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.lumen.assets", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.assets.mapper")
public class AssetsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetsApplication.class, args);
    }
}