package com.lumen.org.controller;

import com.lumen.common.core.domain.R;
import com.lumen.org.dto.DeptNode;
import com.lumen.org.entity.SysDept;
import com.lumen.org.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/org/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    public R<List<DeptNode>> tree() {
        return R.ok(deptService.tree());
    }

    @GetMapping("/list")
    public R<?> list(@RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "10") int pageSize,
                     @RequestParam(required = false) String keyword) {
        return R.ok(deptService.list(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public R<SysDept> get(@PathVariable Long id) {
        return R.ok(deptService.getById(id));
    }

    @PostMapping
    public R<SysDept> create(@RequestBody SysDept dept) {
        return R.ok(deptService.create(dept));
    }

    @PutMapping("/{id}")
    public R<SysDept> update(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setDeptId(id);
        return R.ok(deptService.update(dept));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }
}