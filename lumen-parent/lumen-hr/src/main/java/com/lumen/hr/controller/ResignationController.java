package com.lumen.hr.controller;

import com.lumen.common.core.domain.R;
import com.lumen.hr.dto.SubmitResignationRequest;
import com.lumen.hr.entity.HrResignation;
import com.lumen.hr.service.ResignationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hr/resignation")
@RequiredArgsConstructor
public class ResignationController {

    private final ResignationService resignationService;

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    public R<HrResignation> submit(@RequestBody @Valid SubmitResignationRequest req) {
        return R.ok(resignationService.submit(req));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public R<HrResignation> revoke(@PathVariable Long id) {
        return R.ok(resignationService.revoke(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<HrResignation> get(@PathVariable Long id) {
        return R.ok(resignationService.getById(id));
    }
}
