package com.lumen.file.controller;

import com.lumen.common.core.domain.R;
import com.lumen.file.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class HealthController {

    private final StorageProvider storage;

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("storage", storage.isHealthy() ? "UP" : "DOWN");
        return R.ok(body);
    }
}
