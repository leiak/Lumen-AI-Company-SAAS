package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.contract.entity.ChangeLog;
import com.lumen.contract.service.ChangeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contract/{id}/change-log")
@RequiredArgsConstructor
public class ChangeLogController {

    private final ChangeLogService changeLogService;

    @GetMapping
    public R<List<ChangeLog>> list(@PathVariable Long id) {
        return R.ok(changeLogService.findByContract(id));
    }
}
