package com.lumen.bi.controller;

import com.lumen.bi.dto.GrantPermissionRequest;
import com.lumen.bi.entity.BiPermission;
import com.lumen.bi.service.PermissionService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限接口。安全要求 #7: grant/revoke/check 仅 admin。
 */
@RestController
@RequestMapping("/bi/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 授权。安全要求 #7: 仅 admin。
     */
    @PostMapping("/grant")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiPermission> grant(@RequestBody @Valid GrantPermissionRequest req) {
        return R.ok(permissionService.grant(
            req.getResourceType(),
            Long.parseLong(req.getResourceId()),
            req.getPrincipalType(),
            Long.parseLong(req.getPrincipalId()),
            req.getPermission()));
    }

    @PostMapping("/revoke")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> revoke(@RequestBody @Valid GrantPermissionRequest req) {
        permissionService.revoke(
            req.getResourceType(),
            Long.parseLong(req.getResourceId()),
            req.getPrincipalType(),
            Long.parseLong(req.getPrincipalId()));
        return R.ok();
    }

    /**
     * 安全要求 #6: checkAccess 校验链。
     */
    @GetMapping("/check")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> check(@RequestParam String resourceType,
                            @RequestParam Long resourceId,
                            @RequestParam String principalType,
                            @RequestParam Long principalId) {
        return R.ok(permissionService.checkAccess(resourceType, resourceId, principalType, principalId));
    }

    @GetMapping("/by-resource")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<List<BiPermission>> byResource(@RequestParam String resourceType,
                                            @RequestParam Long resourceId) {
        return R.ok(permissionService.findByResource(resourceType, resourceId));
    }

    @GetMapping("/by-principal")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<List<BiPermission>> byPrincipal(@RequestParam String principalType,
                                              @RequestParam Long principalId) {
        return R.ok(permissionService.findByPrincipal(principalType, principalId));
    }
}