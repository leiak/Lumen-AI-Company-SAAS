package com.lumen.assets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.assets.entity.AstVehicle;
import com.lumen.assets.service.VehicleService;
import com.lumen.common.core.domain.R;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assets/vehicle")
@RequiredArgsConstructor
@Validated
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<IPage<AstVehicle>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(vehicleService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<AstVehicle> get(@PathVariable Long id) {
        return R.ok(vehicleService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstVehicle> save(@RequestBody AstVehicle req) {
        if (req.getId() == null) return R.ok(vehicleService.create(req));
        return R.ok(vehicleService.update(req.getId(), req));
    }

    @PostMapping("/{id}/record-mileage")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstVehicle> recordMileage(@PathVariable Long id, @RequestParam Long mileage) {
        return R.ok(vehicleService.recordMileage(id, mileage, java.time.LocalDate.now()));
    }

    @PostMapping("/{id}/schedule-maintenance")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstVehicle> scheduleMaintenance(@PathVariable Long id, @RequestParam Long nextMileage) {
        return R.ok(vehicleService.scheduleMaintenance(id, nextMileage));
    }
}