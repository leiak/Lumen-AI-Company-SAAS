package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.ExpenseReportRequest;
import com.lumen.finance.entity.FinExpenseReport;
import com.lumen.finance.service.ExpenseReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fin/expense")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseReportService expenseService;

    @PostMapping("/save")
    @PreAuthorize("isAuthenticated()")
    public R<FinExpenseReport> save(@RequestBody @Valid ExpenseReportRequest req) {
        return R.ok(expenseService.saveDraft(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public R<FinExpenseReport> submit(@PathVariable Long id) {
        return R.ok(expenseService.submit(id));
    }

    /**
     * 安全要求 #2: 仅 finance_admin / super_admin / admin 可审批
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinExpenseReport> approve(@PathVariable Long id,
                                       @RequestBody(required = false) java.util.Map<String, String> body) {
        String comment = body == null ? null : body.get("comment");
        return R.ok(expenseService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinExpenseReport> reject(@PathVariable Long id,
                                      @RequestBody(required = false) java.util.Map<String, String> body) {
        String comment = body == null ? null : body.get("comment");
        return R.ok(expenseService.reject(id, comment));
    }

    /**
     * 安全要求 #2: 仅 isAuthenticated()，service 层从 ctx 取 applicantId。
     */
    @GetMapping("/my-list")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinExpenseReport>> myList() {
        return R.ok(expenseService.myList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinExpenseReport> get(@PathVariable Long id) {
        return R.ok(expenseService.get(id));
    }
}
