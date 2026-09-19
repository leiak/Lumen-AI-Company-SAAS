package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 移动端工作流代理接口。
 *
 * <p>P4 阶段不接 Feign；这里返回 stub 数据 + TODO P5 接入 workflow-service。
 * 安全要求：审批操作必须 isAuthenticated + 用户是当前 task assignee（workflow-service 内做）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/mobile/api/workflow")
public class WorkflowProxyController {

    /**
     * 待办任务列表。P5: Feign → workflow-service /workflow/task/todo
     */
    @GetMapping("/todo")
    @PreAuthorize("isAuthenticated()")
    public R<List<Map<String, Object>>> todo() {
        log.debug("Mobile todo (stub) — P5 will call workflow-service");
        return R.ok(List.of());
    }

    /**
     * 审批通过。P5: Feign → workflow-service /workflow/task/approve
     */
    @PostMapping("/{taskId}/approve")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> approve(@PathVariable Long taskId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        log.info("Mobile approve (stub) taskId={}", taskId);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "approved");
        return R.ok(data);
    }

    /**
     * 驳回。P5: Feign → workflow-service /workflow/task/reject
     */
    @PostMapping("/{taskId}/reject")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> reject(@PathVariable Long taskId,
                                         @RequestBody(required = false) Map<String, Object> body) {
        log.info("Mobile reject (stub) taskId={}", taskId);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "rejected");
        return R.ok(data);
    }
}
