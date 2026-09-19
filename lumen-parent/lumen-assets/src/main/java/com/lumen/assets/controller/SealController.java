package com.lumen.assets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.assets.entity.AstSeal;
import com.lumen.assets.entity.AstSealUsage;
import com.lumen.assets.service.SealService;
import com.lumen.common.core.domain.R;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets/seal")
@RequiredArgsConstructor
@Validated
public class SealController {

    private final SealService sealService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<IPage<AstSeal>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sealType,
            @RequestParam(required = false) String status) {
        return R.ok(sealService.page(pageNum, pageSize, keyword, sealType, status));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstSeal> save(@RequestBody AstSeal req) {
        if (req.getId() == null) return R.ok(sealService.create(req));
        return R.ok(sealService.update(req.getId(), req));
    }

    @PostMapping("/{id}/use")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstSealUsage> use(@PathVariable Long id,
                               @RequestParam String documentName,
                               @RequestParam(required = false) String documentId,
                               @RequestParam Long userId,
                               @RequestParam(required = false) Long witnessId) {
        return R.ok(sealService.use(id, documentName, documentId, userId, witnessId));
    }

    @PostMapping("/{id}/return-usage")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstSealUsage> returnUsage(@PathVariable Long id, @RequestParam Long usageId) {
        if (!id.equals(usageId) && usageId != null) {
            // 用 usageId 路径即足够；id 用于保留父资源语义
        }
        return R.ok(sealService.returnUsage(usageId));
    }

    @PostMapping("/{id}/destroy")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstSeal> destroy(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(sealService.destroy(id, reason));
    }

    @GetMapping("/{id}/usages")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstSealUsage>> usages(@PathVariable Long id) {
        return R.ok(sealService.findUsages(id));
    }
}