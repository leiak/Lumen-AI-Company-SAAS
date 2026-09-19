package com.lumen.assets.controller;

import com.lumen.assets.dto.CategoryNode;
import com.lumen.assets.entity.AstCategory;
import com.lumen.assets.service.CategoryService;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets/category")
@RequiredArgsConstructor
@Validated
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<CategoryNode>> tree() {
        return R.ok(categoryService.tree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<AstCategory> get(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstCategory> save(@RequestBody AstCategory req) {
        if (req.getId() == null) return R.ok(categoryService.create(req));
        return R.ok(categoryService.update(req.getId(), req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}