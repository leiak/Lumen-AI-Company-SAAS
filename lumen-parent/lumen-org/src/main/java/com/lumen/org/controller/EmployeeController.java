package com.lumen.org.controller;

import com.lumen.common.core.domain.R;
import com.lumen.org.entity.SysEmployee;
import com.lumen.org.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long deptId,
                     @RequestParam(required = false) String status) {
        return R.ok(employeeService.list(pageNum, pageSize, keyword, deptId, status));
    }

    @GetMapping("/{id}")
    public R<SysEmployee> get(@PathVariable Long id) {
        return R.ok(employeeService.getById(id));
    }

    @GetMapping("/by-user/{userId}")
    public R<SysEmployee> getByUserId(@PathVariable Long userId) {
        return R.ok(employeeService.getByUserId(userId));
    }

    @PostMapping
    public R<SysEmployee> create(@RequestBody SysEmployee employee) {
        return R.ok(employeeService.create(employee));
    }

    @PutMapping("/{id}")
    public R<SysEmployee> update(@PathVariable Long id, @RequestBody SysEmployee employee) {
        employee.setEmployeeId(id);
        return R.ok(employeeService.update(employee));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return R.ok();
    }
}