package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.dto.GenerateStatementRequest;
import com.lumen.sales.entity.Statement;
import com.lumen.sales.service.StatementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sal/statement")
@RequiredArgsConstructor
@Validated
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/list")
    public R<IPage<Statement>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                    @RequestParam(required = false) Long customerId) {
        return R.ok(statementService.page(pageNum, pageSize, customerId));
    }

    @GetMapping("/{id}")
    public R<Statement> get(@PathVariable Long id) {
        return R.ok(statementService.get(id));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','finance')")
    public R<Statement> generate(@RequestBody @Valid GenerateStatementRequest req) {
        return R.ok(statementService.generate(req.getCustomerId(), req.getPeriodStart(), req.getPeriodEnd()));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','finance')")
    public R<Statement> send(@PathVariable Long id) {
        return R.ok(statementService.send(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','finance')")
    public R<Statement> confirm(@PathVariable Long id) {
        return R.ok(statementService.confirm(id));
    }
}