package com.lumen.assets.controller;

import com.lumen.assets.dto.TransferRequest;
import com.lumen.assets.entity.AstTransfer;
import com.lumen.assets.service.TransferService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets/transfer")
@RequiredArgsConstructor
@Validated
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstTransfer> apply(@RequestBody @Valid TransferRequest req) {
        return R.ok(transferService.apply(req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstTransfer> approve(@PathVariable Long id) {
        return R.ok(transferService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstTransfer> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(transferService.reject(id, reason));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstTransfer> complete(@PathVariable Long id) {
        return R.ok(transferService.complete(id));
    }

    @GetMapping("/by-asset/{assetId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstTransfer>> byAsset(@PathVariable Long assetId) {
        return R.ok(transferService.findByAsset(assetId));
    }
}