package com.lumen.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.CreateInoutRequest;
import com.lumen.inventory.entity.InvInout;
import com.lumen.inventory.entity.InvInoutItem;
import com.lumen.inventory.service.InoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/inout")
@RequiredArgsConstructor
@Validated
public class InoutController {

    private final InoutService inoutService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<IPage<InvInout>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return R.ok(inoutService.page(pageNum, pageSize, type, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<InvInout> get(@PathVariable Long id) {
        return R.ok(inoutService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvInoutItem>> items(@PathVariable Long id) {
        return R.ok(inoutService.listItems(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvInout> create(@RequestBody @Valid CreateInoutRequest req) {
        return R.ok(inoutService.create(req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvInout> confirm(@PathVariable Long id) {
        return R.ok(inoutService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvInout> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(inoutService.cancel(id, reason));
    }
}