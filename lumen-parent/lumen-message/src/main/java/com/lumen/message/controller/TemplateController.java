package com.lumen.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.message.entity.MsgTemplate;
import com.lumen.message.service.TemplateService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/message/template")
@RequiredArgsConstructor
@Validated
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<IPage<MsgTemplate>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                       @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(templateService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgTemplate> get(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgTemplate> create(@RequestBody MsgTemplate req) {
        return R.ok(templateService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgTemplate> update(@PathVariable Long id, @RequestBody MsgTemplate req) {
        req.setId(id);
        return R.ok(templateService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}