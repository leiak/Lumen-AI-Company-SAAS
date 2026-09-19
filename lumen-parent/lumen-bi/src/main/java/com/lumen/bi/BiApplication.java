package com.lumen.bi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.lumen.bi", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.bi.mapper")
public class BiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BiApplication.class, args);
    }
}