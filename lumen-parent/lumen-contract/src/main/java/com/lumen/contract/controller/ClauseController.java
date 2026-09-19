package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.contract.entity.Clause;
import com.lumen.contract.service.ClauseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contract/{id}/clauses")
@RequiredArgsConstructor
public class ClauseController {

    private final ClauseService clauseService;

    @GetMapping
    public R<List<Clause>> list(@PathVariable Long id) {
        return R.ok(clauseService.findByContract(id));
    }

    @PostMapping("/bulk-save")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Integer> bulkSave(@PathVariable Long id, @RequestBody @Valid List<Clause> clauses) {
        return R.ok(clauseService.bulkSave(id, clauses));
    }
}
