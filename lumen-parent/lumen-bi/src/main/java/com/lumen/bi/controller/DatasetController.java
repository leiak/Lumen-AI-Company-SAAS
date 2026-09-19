package com.lumen.bi.controller;

import com.lumen.bi.entity.BiDataset;
import com.lumen.bi.service.DatasetService;
import com.lumen.common.core.domain.R;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bi/dataset")
@RequiredArgsConstructor
@Validated
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiDataset>> list() {
        return R.ok(datasetService.list());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiDataset>> active() {
        return R.ok(datasetService.findActive());
    }

    @GetMapping("/by-source-type/{sourceType}")
    @PreAuthorize("isAuthenticated()")
    public R<List<BiDataset>> bySourceType(@PathVariable String sourceType) {
        return R.ok(datasetService.findBySourceType(sourceType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<BiDataset> get(@PathVariable Long id) {
        return R.ok(datasetService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<BiDataset> save(@RequestBody BiDataset req) {
        return R.ok(datasetService.save(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','bi_admin')")
    public R<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return R.ok();
    }

    /**
     * 安全要求 #15: pageSize 1..200。
     */
    @GetMapping("/{code}/preview")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> preview(@PathVariable String code,
                                          @RequestParam(defaultValue = "1") @Min(1) int page,
                                          @RequestParam(defaultValue = "100") @Min(1) @Max(200) int pageSize) {
        return R.ok(datasetService.preview(code, page, pageSize));
    }
}