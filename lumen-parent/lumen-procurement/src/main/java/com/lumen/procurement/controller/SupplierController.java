package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.SaveSupplierRequest;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.entity.ProcSupplierQualification;
import com.lumen.procurement.service.ProcSupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proc/supplier")
@RequiredArgsConstructor
@Validated
public class SupplierController {

    private final ProcSupplierService supplierService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcSupplier>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(supplierService.page(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcSupplier> get(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplier> save(@RequestBody @Valid SaveSupplierRequest req) {
        return R.ok(supplierService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplier> update(@PathVariable Long id, @RequestBody @Valid SaveSupplierRequest req) {
        return R.ok(supplierService.update(id, req));
    }

    @PostMapping("/{id}/blacklist")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplier> blacklist(@PathVariable Long id,
                                     @RequestParam(required = false) String reason) {
        return R.ok(supplierService.blacklist(id, reason));
    }

    @PostMapping("/{id}/unblacklist")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcSupplier> unblacklist(@PathVariable Long id) {
        return R.ok(supplierService.unblacklist(id));
    }

    @GetMapping("/{id}/qualifications")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<ProcSupplierQualification>> qualifications(@PathVariable Long id) {
        return R.ok(supplierService.listQualifications(id));
    }
}