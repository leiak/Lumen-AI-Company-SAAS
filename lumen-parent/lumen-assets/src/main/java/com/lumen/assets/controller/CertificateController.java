package com.lumen.assets.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.assets.entity.AstCertificate;
import com.lumen.assets.service.CertificateService;
import com.lumen.common.core.domain.R;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets/certificate")
@RequiredArgsConstructor
@Validated
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<IPage<AstCertificate>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type) {
        return R.ok(certificateService.page(pageNum, pageSize, keyword, type));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<AstCertificate> save(@RequestBody AstCertificate req) {
        if (req.getId() == null) return R.ok(certificateService.create(req));
        return R.ok(certificateService.update(req.getId(), req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin')")
    public R<Void> delete(@PathVariable Long id) {
        certificateService.delete(id);
        return R.ok();
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('super_admin','admin','assets_admin','user')")
    public R<List<AstCertificate>> expiring(@RequestParam(required = false) Integer days) {
        return R.ok(certificateService.expiringWithin(days));
    }
}