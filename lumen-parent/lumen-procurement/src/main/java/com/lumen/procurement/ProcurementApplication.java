package com.lumen.procurement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.lumen.procurement", "com.lumen.common"})
@EnableDiscoveryClient
@MapperScan("com.lumen.procurement.mapper")
public class ProcurementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcurementApplication.class, args);
    }
}