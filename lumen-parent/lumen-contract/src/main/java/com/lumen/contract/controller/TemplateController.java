package com.lumen.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.contract.entity.ClauseTemplate;
import com.lumen.contract.service.TemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/contract/template")
@RequiredArgsConstructor
@Validated
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/list")
    public R<IPage<ClauseTemplate>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(templateService.page(pageNum, pageSize, keyword));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<ClauseTemplate> save(@RequestBody @Valid ClauseTemplate req) {
        return R.ok(templateService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<ClauseTemplate> update(@PathVariable Long id, @RequestBody @Valid ClauseTemplate req) {
        return R.ok(templateService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/render")
    public R<TemplateService.Rendered> render(@PathVariable Long id,
                                               @RequestBody Map<String, Object> variables) {
        return R.ok(templateService.renderVariables(id, variables));
    }
}
