package com.lumen.finance.controller;

import com.lumen.common.core.domain.R;
import com.lumen.finance.dto.SaveSubjectRequest;
import com.lumen.finance.entity.FinAccountSubject;
import com.lumen.finance.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fin/subject")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public R<List<FinAccountSubject>> tree(@RequestParam(required = false) Long parentId) {
        return R.ok(subjectService.getTree(parentId));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','finance_admin')")
    public R<FinAccountSubject> save(@RequestBody @Valid SaveSubjectRequest req) {
        return R.ok(subjectService.save(req));
    }
}
