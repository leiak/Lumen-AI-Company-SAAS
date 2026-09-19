package com.lumen.mobile.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.mobile.dto.SaveAppVersionRequest;
import com.lumen.mobile.dto.UpdateResponse;
import com.lumen.mobile.entity.MobAppVersion;
import com.lumen.mobile.service.AppVersionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * App 版本管理。
 * {@code /mobile/api/version/check} 公开（App 启动时调用，无需 token）。
 * 其他接口 admin only。
 */
@RestController
@RequestMapping("/mobile")
@RequiredArgsConstructor
@Validated
public class AppVersionController {

    private final AppVersionService appVersionService;

    /**
     * 公开接口 — 客户端调用检查是否需要升级。
     * 安全要求 #10：downloadUrl 在生产必须 signed；当前阶段返回明文 TODO P5。
     */
    @GetMapping("/api/version/check")
    public R<UpdateResponse> checkUpdate(
            @RequestParam @NotBlank
            @Pattern(regexp = "^(android|ios|harmony)$",
                message = "platform must be one of: android / ios / harmony") String platform,
            @RequestParam @NotBlank
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?$",
                message = "currentVersion must be semantic version (e.g. 1.0.0)") String currentVersion) {
        return R.ok(appVersionService.checkUpdate(platform, currentVersion));
    }

    @GetMapping("/api/version/latest")
    public R<MobAppVersion> latest(@RequestParam @NotBlank String platform) {
        return R.ok(appVersionService.latest(platform));
    }

    /** Admin 列表 — pageSize 限制 [1, 200]（安全要求 #15）。 */
    @GetMapping("/admin/version/page")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<IPage<MobAppVersion>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                         @RequestParam(required = false) String platform,
                                         @RequestParam(required = false) String status) {
        return R.ok(appVersionService.page(pageNum, pageSize, platform, status));
    }

    @GetMapping("/admin/version/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<MobAppVersion> get(@PathVariable Long id) {
        return R.ok(appVersionService.get(id));
    }

    @PostMapping("/admin/version")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<MobAppVersion> save(@RequestBody @Valid SaveAppVersionRequest req) {
        return R.ok(appVersionService.save(req));
    }

    @PutMapping("/admin/version/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<MobAppVersion> update(@PathVariable Long id,
                                    @RequestBody @Valid SaveAppVersionRequest req) {
        return R.ok(appVersionService.update(id, req));
    }

    @DeleteMapping("/admin/version/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<Void> delete(@PathVariable Long id) {
        appVersionService.delete(id);
        return R.ok();
    }

    /** draft → released 状态机迁移。 */
    @PostMapping("/admin/version/{id}/release")
    @PreAuthorize("hasAnyRole('super_admin','admin','mobile_admin')")
    public R<MobAppVersion> release(@PathVariable Long id) {
        return R.ok(appVersionService.release(id));
    }
}
