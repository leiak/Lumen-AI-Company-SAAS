package com.lumen.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.SaveWarehouseRequest;
import com.lumen.inventory.entity.InvLocation;
import com.lumen.inventory.entity.InvWarehouse;
import com.lumen.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/warehouse")
@RequiredArgsConstructor
@Validated
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<IPage<InvWarehouse>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(warehouseService.page(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<InvWarehouse> get(@PathVariable Long id) {
        return R.ok(warehouseService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvWarehouse> save(@RequestBody @Valid SaveWarehouseRequest req) {
        return R.ok(warehouseService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvWarehouse> update(@PathVariable Long id, @RequestBody @Valid SaveWarehouseRequest req) {
        return R.ok(warehouseService.update(id, req));
    }

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvLocation>> locations(@PathVariable Long id) {
        return R.ok(warehouseService.listLocations(id));
    }
}