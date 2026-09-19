package com.lumen.inventory.controller;

import com.lumen.common.core.domain.R;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvStock>> list(@RequestParam(required = false) Long warehouseId,
                                  @RequestParam(required = false) Long itemId) {
        if (warehouseId != null) return R.ok(stockService.findByWarehouse(warehouseId));
        if (itemId != null) return R.ok(stockService.findByItem(itemId));
        return R.ok(stockService.findByItem(null));
    }

    @GetMapping("/{itemId}/available")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvStock>> available(@PathVariable Long itemId,
                                       @RequestParam Long warehouseId) {
        return R.ok(stockService.findAvailable(itemId, warehouseId));
    }
}