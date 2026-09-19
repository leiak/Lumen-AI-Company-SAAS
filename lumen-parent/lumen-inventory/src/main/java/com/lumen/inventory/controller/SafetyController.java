package com.lumen.inventory.controller;

import com.lumen.common.core.domain.R;
import com.lumen.inventory.entity.InvSafetyStock;
import com.lumen.inventory.service.SafetyStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyStockService safetyStockService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvSafetyStock>> list(@RequestParam(required = false) Long warehouseId) {
        if (warehouseId == null) {
            return R.ok(List.of());
        }
        return R.ok(safetyStockService.findByWarehouse(warehouseId));
    }

    @PostMapping("/{warehouseId}/{itemId}/check")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvSafetyStock> check(@PathVariable Long warehouseId, @PathVariable Long itemId) {
        return R.ok(safetyStockService.check(warehouseId, itemId));
    }

    @GetMapping("/low")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvSafetyStock>> low() {
        return R.ok(safetyStockService.findLowStock());
    }

    @GetMapping("/overstock")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvSafetyStock>> overstock() {
        return R.ok(safetyStockService.findOverstock());
    }
}