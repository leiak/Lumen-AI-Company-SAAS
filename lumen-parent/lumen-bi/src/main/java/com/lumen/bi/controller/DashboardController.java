package com.lumen.bi.controller;

import com.lumen.bi.dto.SaveDashboardRequest;
import com.lumen.bi.entity.BiDashboard;
import com.lumen.bi.service.DashboardService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bi/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiDashboard>> list() {
        return R.ok(dashboardService.list());
    }

    @GetMapping("/published")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiDashboard>> published() {
        return R.ok(dashboardService.findPublished());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<BiDashboard> get(@PathVariable Long id) {
        return R.ok(dashboardService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiDashboard> save(@RequestBody @Valid SaveDashboardRequest req) {
        return R.ok(dashboardService.save(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> delete(@PathVariable Long id) {
        dashboardService.delete(id);
        return R.ok();
    }

    /**
     * 安全要求 #8: publish (draft → published)。
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiDashboard> publish(@PathVariable Long id) {
        return R.ok(dashboardService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiDashboard> archive(@PathVariable Long id) {
        return R.ok(dashboardService.archive(id));
    }

    /**
     * 安全要求 #6: getFullDashboard 必须 checkAccess。
     */
    @GetMapping("/{id}/full")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> full(@PathVariable Long id) {
        return R.ok(dashboardService.getFullDashboard(id));
    }
}