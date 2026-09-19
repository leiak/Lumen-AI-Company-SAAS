package com.lumen.assets.controller;

import com.lumen.assets.dto.StocktakeItemRequest;
import com.lumen.assets.entity.AstStocktake;
import com.lumen.assets.entity.AstStocktakeItem;
import com.lumen.assets.service.StocktakeService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/assets/stocktake")
@RequiredArgsConstructor
@Validated
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstStocktake> start(@RequestParam String code,
                                 @RequestParam String period,
                                 @RequestParam Long deptId,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime plannedAt) {
        return R.ok(stocktakeService.start(code, period, deptId, plannedAt));
    }

    @PostMapping("/{id}/submit-item")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstStocktakeItem> submitItem(@RequestBody @Valid StocktakeItemRequest req) {
        return R.ok(stocktakeService.submitItem(req));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstStocktake> complete(@PathVariable Long id) {
        return R.ok(stocktakeService.complete(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstStocktakeItem>> items(@PathVariable Long id) {
        return R.ok(stocktakeService.findItems(id));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstStocktake>> search(@RequestParam String period, @RequestParam Long deptId) {
        return R.ok(stocktakeService.findByPeriodAndDept(period, deptId));
    }
}