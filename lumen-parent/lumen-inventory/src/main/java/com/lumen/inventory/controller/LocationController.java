package com.lumen.inventory.controller;

import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.SaveLocationRequest;
import com.lumen.inventory.entity.InvLocation;
import com.lumen.inventory.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/location")
@RequiredArgsConstructor
@Validated
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvLocation> save(@RequestBody @Valid SaveLocationRequest req) {
        return R.ok(locationService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvLocation> update(@PathVariable Long id, @RequestBody @Valid SaveLocationRequest req) {
        return R.ok(locationService.update(id, req));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvLocation>> byWarehouse(@PathVariable Long warehouseId) {
        return R.ok(locationService.findByWarehouse(warehouseId));
    }
}