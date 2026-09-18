package com.lumen.system.controller;

import com.lumen.common.core.domain.R;
import com.lumen.system.entity.SysConfig;
import com.lumen.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword) {
        return R.ok(configService.list(pageNum, pageSize, keyword));
    }

    @GetMapping("/key/{key}")
    public R<String> getByKey(@PathVariable String key) {
        return R.ok(configService.getValue(key));
    }

    @PutMapping("/{id}")
    public R<SysConfig> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(configService.update(id, body.get("configValue")));
    }
}
