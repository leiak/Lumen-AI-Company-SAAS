package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.dto.SaveStructureRequest;
import com.lumen.payroll.entity.PaySalaryStructure;
import com.lumen.payroll.service.SalaryStructureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay/structure")
@RequiredArgsConstructor
public class StructureController {

    private final SalaryStructureService structureService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PaySalaryStructure> save(@RequestBody @Valid SaveStructureRequest req) {
        return R.ok(structureService.save(req));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySalaryStructure>> list() {
        return R.ok(structureService.list());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySalaryStructure>> active() {
        return R.ok(structureService.findActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PaySalaryStructure> get(@PathVariable Long id) {
        return R.ok(structureService.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Void> delete(@PathVariable Long id) {
        structureService.delete(id);
        return R.ok(null);
    }
}