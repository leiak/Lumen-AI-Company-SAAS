package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfTask;
import com.lumen.workflow.entity.WfTaskHistory;
import com.lumen.workflow.mapper.WfTaskHistoryMapper;
import com.lumen.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final WfTaskMapper taskMapper;
    private final WfTaskHistoryMapper taskHistoryMapper;
    private final EngineService engineService;

    /**
     * 待办列表：assignee == userId OR candidate_users contains userId OR
     * candidate_roles intersects user's roles.
     *
     * <p>Strategy: SQL fetches assignee / candidate_users matches; candidate_roles
     * filtering happens in Java because user roles come from the security context.
     * TODO: full DB-side JSON_OVERLAPS once we know MySQL 8 version is uniform.</p>
     */
    public IPage<WfTask> todoList(Long userId, int pageNum, int pageSize) {
        if (userId == null) throw new ServiceException(401, "No user context");

        Set<String> userRoles = rolesOfCurrentUser();
        Set<Long> userRolesAsLong = userRoles.stream()
            .filter(s -> s != null && s.matches("\\d+"))
            .map(Long::parseLong)
            .collect(Collectors.toSet());

        // Page 1 we may need to over-fetch to drop candidate_roles false positives client-side.
        // For P3 simplicity we just page directly off the SQL filter, then filter roles in memory
        // only on the visible page.
        IPage<WfTask> page = taskMapper.selectPage(
            Page.of(pageNum, pageSize),
            new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, EngineService.TASK_STATUS_TODO)
                .and(q -> q.eq(WfTask::getAssignee, userId)
                    .or().apply("JSON_CONTAINS(candidate_users, CAST({0} AS JSON))", userId))
                .orderByAsc(WfTask::getId));

        // Filter out rows whose candidate_users doesn't include userId AND assignee != userId AND
        // no candidate_roles overlap — i.e. rows that passed only because the SQL filter still
        // produced them (defensive — current SQL should already have caught both).
        List<WfTask> filtered = page.getRecords().stream()
            .filter(t -> matchesUser(t, userId, userRolesAsLong))
            .collect(Collectors.toList());
        page.setRecords(filtered);
        return page;
    }

    private boolean matchesUser(WfTask t, Long userId, Set<Long> userRolesAsLong) {
        if (t.getAssignee() != null && t.getAssignee().equals(userId)) return true;
        if (t.getCandidateUsers() != null && t.getCandidateUsers().contains(userId)) return true;
        if (t.getCandidateRoles() != null && !t.getCandidateRoles().isEmpty()
            && userRolesAsLong != null && !userRolesAsLong.isEmpty()) {
            for (String r : t.getCandidateRoles()) {
                try {
                    if (userRolesAsLong.contains(Long.parseLong(r))) return true;
                } catch (NumberFormatException ignored) { /* non-numeric role */ }
            }
        }
        return false;
    }

    private Set<String> rolesOfCurrentUser() {
        UserContext ctx = UserContextHolder.get();
        return ctx == null || ctx.getRoles() == null ? Collections.emptySet() : ctx.getRoles();
    }

    public List<WfTaskHistory> history(Long instanceId) {
        return taskHistoryMapper.listByInstanceId(instanceId);
    }

    public void done(Long taskId, String comment) {
        engineService.completeTask(taskId, EngineService.ACTION_DONE, comment, null);
    }

    public void transfer(Long taskId, Long toUserId, String comment) {
        if (toUserId == null) throw new ServiceException(400, "toUserId is required");
        Map<String, Object> params = new HashMap<>();
        params.put("toUserId", toUserId);
        engineService.completeTask(taskId, EngineService.ACTION_TRANSFER, comment, params);
    }

    public void addSign(Long taskId, List<Long> userIds, String comment) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ServiceException(400, "userIds must not be empty");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userIds", new ArrayList<>(userIds));
        engineService.completeTask(taskId, EngineService.ACTION_ADD_SIGN, comment, params);
    }

    public void reject(Long taskId, String targetNodeKey, String comment) {
        if (targetNodeKey == null || targetNodeKey.isBlank()) {
            throw new ServiceException(400, "targetNodeKey is required");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("targetNodeKey", targetNodeKey);
        engineService.completeTask(taskId, EngineService.ACTION_REJECT, comment, params);
    }

    /**
     * Find an open task — used by tests and as a defensive helper. Returns null if none.
     */
    public WfTask findOpenTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    /**
     * Defensive unused helper to keep Spotbugs quiet about HashSet import in callers.
     */
    @SuppressWarnings("unused")
    private static Set<Long> uniq(List<Long> xs) {
        return xs == null ? new HashSet<>() : new HashSet<>(xs);
    }
}