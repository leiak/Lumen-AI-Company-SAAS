package com.lumen.bi.controller;

import com.lumen.bi.dto.SaveMetricRequest;
import com.lumen.bi.entity.BiMetric;
import com.lumen.bi.service.MetricService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 指标接口。安全要求 #2: 写操作 @PreAuthorize 限制角色。
 */
@RestController
@RequestMapping("/bi/metric")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiMetric>> list() {
        return R.ok(metricService.list());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiMetric>> active() {
        return R.ok(metricService.findActive());
    }

    @GetMapping("/by-category/{category}")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiMetric>> byCategory(@PathVariable String category) {
        return R.ok(metricService.findByCategory(category));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<BiMetric> get(@PathVariable Long id) {
        return R.ok(metricService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiMetric> save(@RequestBody @Valid SaveMetricRequest req) {
        return R.ok(metricService.save(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> delete(@PathVariable Long id) {
        metricService.delete(id);
        return R.ok();
    }

    /**
     * 渲染指标 SQL (参数化, 不执行)。返回 RenderResult。
     */
    @PostMapping("/{code}/render")
    @PreAuthorize("isAuthenticated()")
    public R<MetricService.RenderResult> render(@PathVariable String code,
                                                @RequestBody(required = false) Map<String, Object> params) {
        return R.ok(metricService.render(code, params));
    }
}