package com.lumen.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.SaveItemRequest;
import com.lumen.inventory.entity.InvItem;
import com.lumen.inventory.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inv/item")
@RequiredArgsConstructor
@Validated
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<IPage<InvItem>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return R.ok(itemService.page(pageNum, pageSize, keyword, category, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<InvItem> get(@PathVariable Long id) {
        return R.ok(itemService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvItem> save(@RequestBody @Valid SaveItemRequest req) {
        return R.ok(itemService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvItem> update(@PathVariable Long id, @RequestBody @Valid SaveItemRequest req) {
        return R.ok(itemService.update(id, req));
    }
}