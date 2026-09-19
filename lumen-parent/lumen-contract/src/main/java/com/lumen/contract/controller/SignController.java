package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.contract.dto.SignTaskRequest;
import com.lumen.contract.entity.SignTask;
import com.lumen.contract.service.SignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;

    @PostMapping("/{id}/sign/create-task")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<SignTask> createTask(@PathVariable Long id, @RequestBody @Valid SignTaskRequest req) {
        return R.ok(signService.createTask(id, req.getSignerUserId(), req.getRole(),
            req.getMethod(), req.getProvider()));
    }

    @PostMapping("/{id}/sign/{tid}/signed")
    public R<SignTask> signed(@PathVariable Long id, @PathVariable Long tid,
                                @RequestBody(required = false) Map<String, Object> body) {
        String externalTaskId = body == null ? null : (String) body.get("externalTaskId");
        Long fileId = body == null || body.get("fileId") == null ? null
            : Long.valueOf(String.valueOf(body.get("fileId")));
        return R.ok(signService.markSigned(tid, externalTaskId, fileId));
    }

    @PostMapping("/{id}/sign/{tid}/rejected")
    public R<SignTask> rejected(@PathVariable Long id, @PathVariable Long tid,
                                  @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        return R.ok(signService.markRejected(tid, reason));
    }

    /** Always uses ctx userId — does NOT accept userId from request. */
    @GetMapping("/sign/my-tasks")
    public R<List<SignTask>> myTasks() {
        return R.ok(signService.myTasks());
    }

    @GetMapping("/{id}/sign/tasks")
    public R<List<SignTask>> contractTasks(@PathVariable Long id) {
        return R.ok(signService.findByContract(id));
    }
}
