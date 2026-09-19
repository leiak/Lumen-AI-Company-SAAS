package com.lumen.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.StartStocktakeRequest;
import com.lumen.inventory.dto.StocktakeItemRequest;
import com.lumen.inventory.entity.InvStocktake;
import com.lumen.inventory.entity.InvStocktakeItem;
import com.lumen.inventory.service.StocktakeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/stocktake")
@RequiredArgsConstructor
@Validated
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<IPage<InvStocktake>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String status) {
        return R.ok(stocktakeService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<InvStocktake> get(@PathVariable Long id) {
        return R.ok(stocktakeService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvStocktakeItem>> items(@PathVariable Long id) {
        return R.ok(stocktakeService.listItems(id));
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvStocktake> start(@RequestBody @Valid StartStocktakeRequest req) {
        return R.ok(stocktakeService.start(req));
    }

    @PostMapping("/{id}/submit-item")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvStocktakeItem> submitItem(@PathVariable Long id, @RequestBody @Valid StocktakeItemRequest req) {
        // id 路径变量忽略,StocktakeItemRequest 内 itemId 必填
        return R.ok(stocktakeService.submitItem(req));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvStocktake> complete(@PathVariable Long id) {
        return R.ok(stocktakeService.complete(id));
    }
}