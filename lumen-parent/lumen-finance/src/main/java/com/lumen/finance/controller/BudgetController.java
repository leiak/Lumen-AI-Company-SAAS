package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.BudgetCheckRequest;
import com.lumen.finance.entity.FinBudget;
import com.lumen.finance.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fin/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinBudget> save(@RequestBody FinBudget req) {
        return R.ok(budgetService.save(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<FinBudget> get(@PathVariable Long id) {
        return R.ok(budgetService.get(id));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinBudget>> list(@RequestParam String period, @RequestParam Long deptId) {
        return R.ok(budgetService.findByPeriodAndDept(period, deptId));
    }

    /**
     * 检查预算是否够用;返回 { ok: bool }。
     */
    @PostMapping("/check")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> check(@RequestBody @Valid BudgetCheckRequest req) {
        boolean ok = budgetService.check(req.getPeriod(), req.getDeptId(),
            req.getSubjectId(), req.getAmount());
        Map<String, Object> body = new HashMap<>();
        body.put("ok", ok);
        return R.ok(body);
    }
}
