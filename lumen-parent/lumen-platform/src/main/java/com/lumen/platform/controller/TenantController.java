package com.lumen.platform.controller;

import com.lumen.common.core.domain.R;
import com.lumen.platform.entity.Tenant;
import com.lumen.platform.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword) {
        return R.ok(tenantService.list(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public R<Tenant> get(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @PostMapping("/create")
    public R<Tenant> create(@RequestBody Tenant tenant) {
        return R.ok(tenantService.create(tenant));
    }

    @PostMapping("/{id}/switch-mode")
    public R<Tenant> switchMode(@PathVariable Long id, @RequestParam String mode) {
        return R.ok(tenantService.switchMode(id, mode));
    }
}
