package com.lumen.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.workflow.dto.CancelInstanceRequest;
import com.lumen.workflow.dto.StartInstanceRequest;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.service.InstanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
@Validated
public class InstanceController {

    private final InstanceService instanceService;

    @PostMapping("/start")
    public R<Long> start(@RequestBody @Valid StartInstanceRequest req) {
        Long id = instanceService.start(req.getDefKey(), req.getBusinessKey(), req.getVariables());
        return R.ok(id);
    }

    @GetMapping("/{id}")
    public R<WfInstance> get(@PathVariable Long id) {
        return R.ok(instanceService.get(id));
    }

    @GetMapping("/page")
    public R<IPage<WfInstance>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                     @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize) {
        return R.ok(instanceService.pageByCurrentUser(pageNum, pageSize));
    }

    @PostMapping("/{id}/cancel")
    public R<WfInstance> cancel(@PathVariable Long id, @RequestBody(required = false) CancelInstanceRequest req) {
        String reason = (req == null) ? null : req.getReason();
        return R.ok(instanceService.cancel(id, reason));
    }
}