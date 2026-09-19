package com.lumen.bi.controller;

import com.lumen.bi.entity.BiWidget;
import com.lumen.bi.service.WidgetService;
import com.lumen.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bi/widget")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;

    @GetMapping("/by-dashboard/{dashboardId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiWidget>> byDashboard(@PathVariable Long dashboardId) {
        return R.ok(widgetService.findByDashboard(dashboardId));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiWidget> save(@RequestBody BiWidget req) {
        return R.ok(widgetService.save(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> delete(@PathVariable Long id) {
        widgetService.delete(id);
        return R.ok();
    }

    /**
     * 安全要求 #13: Widget 数据返回前必须 checkAccess; 失败 403。
     */
    @GetMapping("/{id}/data")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> data(@PathVariable Long id) {
        return R.ok(widgetService.getWidgetData(id));
    }
}