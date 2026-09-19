package com.lumen.assets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.assets.dto.SaveAssetRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.service.AssetService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
@Validated
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<IPage<AstAsset>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId) {
        return R.ok(assetService.page(pageNum, pageSize, keyword, categoryId, status, deptId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<AstAsset> get(@PathVariable Long id) {
        return R.ok(assetService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstAsset> save(@RequestBody @Valid SaveAssetRequest req) {
        return R.ok(assetService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstAsset> update(@PathVariable Long id, @RequestBody @Valid SaveAssetRequest req) {
        return R.ok(assetService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/scrapped")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstAsset> scrapped(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(assetService.scrapped(id, reason));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstAsset> restore(@PathVariable Long id) {
        return R.ok(assetService.restore(id));
    }
}