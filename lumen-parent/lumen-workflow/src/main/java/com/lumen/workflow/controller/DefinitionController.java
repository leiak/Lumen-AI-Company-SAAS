package com.lumen.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.workflow.entity.WfDefinition;
import com.lumen.workflow.service.DefinitionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow/definition")
@RequiredArgsConstructor
@Validated
public class DefinitionController {

    private final DefinitionService definitionService;

    @GetMapping("/page")
    public R<IPage<WfDefinition>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                       @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String category) {
        return R.ok(definitionService.page(pageNum, pageSize, keyword, category));
    }

    @GetMapping("/{id}")
    public R<WfDefinition> get(@PathVariable Long id) {
        return R.ok(definitionService.getById(id));
    }

    @PostMapping
    public R<WfDefinition> create(@RequestBody WfDefinition req) {
        return R.ok(definitionService.create(req));
    }

    @PutMapping("/{id}")
    public R<WfDefinition> update(@PathVariable Long id, @RequestBody WfDefinition req) {
        req.setId(id);
        return R.ok(definitionService.update(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        definitionService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/deploy")
    public R<WfDefinition> deploy(@PathVariable Long id) {
        return R.ok(definitionService.deploy(id));
    }
}