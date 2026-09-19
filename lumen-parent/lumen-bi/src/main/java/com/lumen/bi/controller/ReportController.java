package com.lumen.bi.controller;

import com.lumen.bi.dto.SaveReportRequest;
import com.lumen.bi.entity.BiReport;
import com.lumen.bi.service.ReportService;
import com.lumen.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bi/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiReport>> list() {
        return R.ok(reportService.list());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiReport>> active() {
        return R.ok(reportService.findActive());
    }

    @GetMapping("/by-schedule/{schedule}")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiReport>> bySchedule(@PathVariable String schedule) {
        return R.ok(reportService.findBySchedule(schedule));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<BiReport> get(@PathVariable Long id) {
        return R.ok(reportService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiReport> save(@RequestBody @Valid SaveReportRequest req) {
        return R.ok(reportService.save(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/run-now")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Map<String, Object>> runNow(@PathVariable Long id,
                                         @RequestParam(required = false) String format) {
        return R.ok(reportService.runNow(id, format));
    }

    /**
     * 安全要求 #9: schedule 计算下次执行时间。
     */
    @GetMapping("/{id}/scheduled")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> scheduled(@PathVariable Long id) {
        return R.ok(reportService.schedule(id));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Map<String, Object>> send(@PathVariable Long id,
                                       @RequestBody(required = false) List<String> recipients) {
        return R.ok(reportService.sendReport(id, recipients));
    }
}