package com.lumen.platform.controller;

import com.lumen.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform")
public class HealthController {
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("platform-service is UP");
    }
}