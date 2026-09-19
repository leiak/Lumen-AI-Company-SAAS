package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.entity.ProcSupplierQualification;
import com.lumen.procurement.service.ProcSupplierQualificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proc/qualification")
@RequiredArgsConstructor
@Validated
public class QualificationController {

    private final ProcSupplierQualificationService qualificationService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcSupplierQualification>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status) {
        return R.ok(qualificationService.page(pageNum, pageSize, supplierId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcSupplierQualification> get(@PathVariable Long id) {
        return R.ok(qualificationService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplierQualification> save(@RequestBody ProcSupplierQualification req) {
        return R.ok(qualificationService.create(req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplierQualification> approve(@PathVariable Long id) {
        return R.ok(qualificationService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplierQualification> reject(@PathVariable Long id) {
        return R.ok(qualificationService.reject(id));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<ProcSupplierQualification>> expiring(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return R.ok(qualificationService.findExpiring(days));
    }
}