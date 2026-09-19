package com.lumen.assets.controller;

import com.lumen.assets.entity.AstDepreciation;
import com.lumen.assets.service.DepreciationService;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets/depreciation")
@RequiredArgsConstructor
@Validated
public class DepreciationController {

    private final DepreciationService depreciationService;

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<List<AstDepreciation>> run(@RequestParam String period) {
        return R.ok(depreciationService.runMonthly(period));
    }
}