package com.lumen.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.dto.AddSignRequest;
import com.lumen.workflow.dto.RejectRequest;
import com.lumen.workflow.dto.TaskActionRequest;
import com.lumen.workflow.dto.TransferRequest;
import com.lumen.workflow.entity.WfTask;
import com.lumen.workflow.entity.WfTaskHistory;
import com.lumen.workflow.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflow/task")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/todo")
    public R<IPage<WfTask>> todo(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "No user context");
        return R.ok(taskService.todoList(uid, pageNum, pageSize));
    }

    @GetMapping("/history")
    public R<List<WfTaskHistory>> history(@RequestParam Long instanceId) {
        return R.ok(taskService.history(instanceId));
    }

    @PostMapping("/{id}/done")
    public R<Void> done(@PathVariable Long id, @RequestBody(required = false) @Valid TaskActionRequest req) {
        String comment = req == null ? null : req.getComment();
        taskService.done(id, comment);
        return R.ok();
    }

    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@PathVariable Long id, @RequestBody @Valid TransferRequest req) {
        taskService.transfer(id, req.getToUserId(), req.getComment());
        return R.ok();
    }

    @PostMapping("/{id}/addSign")
    public R<Void> addSign(@PathVariable Long id, @RequestBody @Valid AddSignRequest req) {
        taskService.addSign(id, req.getUserIds(), req.getComment());
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody @Valid RejectRequest req) {
        taskService.reject(id, req.getTargetNodeKey(), req.getComment());
        return R.ok();
    }
}