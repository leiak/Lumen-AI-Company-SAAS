package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.dto.SaveEmployeeSalaryRequest;
import com.lumen.payroll.entity.PayEmployeeSalary;
import com.lumen.payroll.service.EmployeeSalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay/employee-salary")
@RequiredArgsConstructor
public class EmployeeSalaryController {

    private final EmployeeSalaryService employeeSalaryService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayEmployeeSalary> save(@RequestBody @Valid SaveEmployeeSalaryRequest req) {
        return R.ok(employeeSalaryService.save(req));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PayEmployeeSalary>> list() {
        return R.ok(employeeSalaryService.listAll());
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PayEmployeeSalary>> listByEmployee(@PathVariable Long employeeId) {
        return R.ok(employeeSalaryService.listByEmployee(employeeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PayEmployeeSalary> get(@PathVariable Long id) {
        return R.ok(employeeSalaryService.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Void> delete(@PathVariable Long id) {
        employeeSalaryService.delete(id);
        return R.ok(null);
    }
}