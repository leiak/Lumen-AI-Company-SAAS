package com.lumen.assets.controller;

import com.lumen.assets.dto.ApplyCustodyRequest;
import com.lumen.assets.entity.AstCustody;
import com.lumen.assets.service.CustodyService;
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
@RequestMapping("/assets/custody")
@RequiredArgsConstructor
@Validated
public class CustodyController {

    private final CustodyService custodyService;

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstCustody> apply(@RequestBody @Valid ApplyCustodyRequest req) {
        return R.ok(custodyService.apply(req));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstCustody> returnCustody(@PathVariable Long id,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime returnAt) {
        return R.ok(custodyService.returnCustody(id,
            returnAt != null ? returnAt : LocalDateTime.now()));
    }

    @GetMapping("/by-asset/{assetId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstCustody>> byAsset(@PathVariable Long assetId) {
        return R.ok(custodyService.findByAsset(assetId));
    }

    @GetMapping("/active/{custodianId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstCustody>> active(@PathVariable Long custodianId) {
        return R.ok(custodyService.findActiveByCustodian(custodianId));
    }
}