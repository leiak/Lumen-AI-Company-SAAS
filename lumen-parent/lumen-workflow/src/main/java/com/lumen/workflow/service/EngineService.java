package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfDefinition;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.entity.WfTask;
import com.lumen.workflow.entity.WfTaskHistory;
import com.lumen.workflow.mapper.WfDefinitionMapper;
import com.lumen.workflow.mapper.WfInstanceMapper;
import com.lumen.workflow.mapper.WfTaskHistoryMapper;
import com.lumen.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified BPMN state machine. Only handles a linear sequence of {@code <userTask>}
 * nodes for P3 — full gateway / parallel / inclusive support is left as TODO.
 *
 * <p>BPMN parsing uses a tiny regex extraction; full BPMN 2.0 (sequenceFlow, gateways,
 * boundary events) is on the roadmap.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EngineService {

    /** Task action codes — used in WfTask.status and WfTaskHistory.action. */
    public static final String ACTION_DONE = "done";
    public static final String ACTION_TRANSFER = "transfer";
    public static final String ACTION_ADD_SIGN = "addSign";
    public static final String ACTION_REJECT = "reject";

    public static final int TASK_STATUS_TODO = 0;
    public static final int TASK_STATUS_DONE = 1;
    public static final int TASK_STATUS_TRANSFER = 2;
    public static final int TASK_STATUS_ADD_SIGN = 3;
    public static final int TASK_STATUS_REJECT = 4;

    public static final int INSTANCE_STATUS_RUNNING = 0;
    public static final int INSTANCE_STATUS_COMPLETED = 1;
    public static final int INSTANCE_STATUS_CANCELLED = 2;

    /** Bypass role for super-admins (consistent with {@code lumen-org/EmployeeController}). */
    public static final String SUPER_ADMIN_ROLE = "super_admin";

    /**
     * Robust BPMN matcher: accepts {@code <userTask>} with id/name in either order.
     * Group 1 = id, Group 2 = name (single match per {@code <userTask>}). If a node
     * is missing an attribute, that group is null.
     */
    private static final Pattern USER_TASK_PATTERN = Pattern.compile(
        "<userTask\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_ATTR =
        Pattern.compile("\\bid\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern NAME_ATTR =
        Pattern.compile("\\bname\\s*=\\s*\"([^\"]+)\"");

    private final WfDefinitionMapper definitionMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfTaskHistoryMapper taskHistoryMapper;

    /**
     * Start a workflow instance from the latest published definition.
     *
     * @param defKey       process definition key
     * @param businessKey  external business identifier (must be unique per active instance)
     * @param variables    optional process variables (JSON column)
     * @return new instance id
     */
    @Transactional
    public Long startInstance(String defKey, String businessKey, Map<String, Object> variables) {
        WfDefinition def = definitionMapper.selectLatestByKey(defKey);
        if (def == null) {
            throw new ServiceException(404, "Process definition not found: " + defKey);
        }
        if (def.getStatus() == null || def.getStatus() != 1) {
            throw new ServiceException(409, "Process definition not published: " + defKey);
        }

        WfInstance existing = instanceMapper.selectByBusinessKey(businessKey);
        if (existing != null) {
            throw new ServiceException(409, "Active instance already exists for businessKey=" + businessKey);
        }

        // BPMN parsing (留 TODO): find first userTask id; full parser later.
        List<UserTaskNode> nodes = collectUserTasks(def.getBpmnXml());
        if (nodes.isEmpty()) {
            throw new ServiceException(400, "BPMN has no userTask: " + defKey);
        }
        UserTaskNode first = nodes.get(0);

        UserContext ctx = requireUserContext();
        Long starter = ctx.getUserId();
        Long tenantId = ctx.getTenantId();
        if (tenantId == null) {
            throw new ServiceException(401, "Missing tenant context");
        }

        WfInstance instance = new WfInstance();
        instance.setDefinitionId(def.getId());
        instance.setDefKey(def.getDefKey());
        instance.setBusinessKey(businessKey);
        instance.setTenantId(tenantId);
        instance.setStatus(INSTANCE_STATUS_RUNNING);
        instance.setCurrentNodeKey(first.id);
        instance.setVariables(variables != null ? variables : new HashMap<>());
        instance.setStarter(starter != null ? starter : 0L);
        instance.setStartTime(LocalDateTime.now());
        try {
            instanceMapper.insert(instance);
        } catch (DuplicateKeyException ex) {
            // Unique (business_key, deleted) index — concurrent inserts race here.
            throw new ServiceException(409, "Active instance exists for businessKey=" + businessKey, ex);
        }

        WfTask firstTask = new WfTask();
        firstTask.setInstanceId(instance.getId());
        firstTask.setNodeKey(first.id);
        firstTask.setNodeName(first.name);
        firstTask.setStatus(TASK_STATUS_TODO);
        taskMapper.insert(firstTask);

        log.info("Workflow instance started id={} defKey={} businessKey={} firstNode={}",
            instance.getId(), defKey, businessKey, first.id);
        return instance.getId();
    }

    /**
     * Apply a task action and advance the workflow.
     *
     * @param taskId  task id
     * @param action  one of {@link #ACTION_DONE}, {@link #ACTION_TRANSFER}, {@link #ACTION_ADD_SIGN}, {@link #ACTION_REJECT}
     * @param comment operator comment
     * @param params  action-specific (toUserId / userIds[] / targetNodeKey)
     */
    @Transactional
    public void completeTask(Long taskId, String action, String comment, Map<String, Object> params) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(404, "Task not found: " + taskId);
        }
        if (task.getStatus() == null || task.getStatus() != TASK_STATUS_TODO) {
            throw new ServiceException(409, "Task already completed: " + taskId);
        }

        // Authorization: must be assignee / candidate user / candidate role (or super_admin).
        UserContext ctx = requireUserContext();
        assertCanActOnTask(task, ctx);

        WfInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new ServiceException(404, "Instance not found: " + task.getInstanceId());
        }
        if (instance.getStatus() == null || instance.getStatus() != INSTANCE_STATUS_RUNNING) {
            throw new ServiceException(409, "Instance not running: " + instance.getId());
        }

        WfDefinition def = definitionMapper.selectById(instance.getDefinitionId());
        if (def == null) {
            throw new ServiceException(404, "Definition not found: " + instance.getDefinitionId());
        }

        Long operator = ctx.getUserId();
        Long operatorOrZero = operator != null ? operator : 0L;

        // 1) Write history first — preserves the audit trail even if subsequent steps fail
        WfTaskHistory history = new WfTaskHistory();
        history.setInstanceId(instance.getId());
        history.setNodeKey(task.getNodeKey());
        history.setAssignee(task.getAssignee());
        history.setAction(action);
        history.setComment(comment);
        history.setOperatedBy(operatorOrZero);
        history.setOperatedTime(LocalDateTime.now());
        taskHistoryMapper.insert(history);

        // 2) Mark the current task with the action-specific status.
        //    updateById is guarded by @Version (optimistic lock) — concurrent done
        //    calls collide with affected_rows=0 → throws OptimisticLockingFailureException.
        int newTaskStatus = switch (action) {
            case ACTION_DONE -> TASK_STATUS_DONE;
            case ACTION_TRANSFER -> TASK_STATUS_TRANSFER;
            case ACTION_ADD_SIGN -> TASK_STATUS_ADD_SIGN;
            case ACTION_REJECT -> TASK_STATUS_REJECT;
            default -> throw new ServiceException(400, "Unknown action: " + action);
        };
        task.setStatus(newTaskStatus);
        task.setComment(comment);
        task.setCompleteTime(LocalDateTime.now());
        taskMapper.updateById(task);

        // 3) Action-specific fan-out
        switch (action) {
            case ACTION_DONE -> advanceToNext(def, instance);
            case ACTION_TRANSFER -> handleTransfer(task, params);
            case ACTION_ADD_SIGN -> handleAddSign(task, params);
            case ACTION_REJECT -> handleReject(def, instance, task, params);
            default -> {
                /* never reached */
            }
        }
        log.info("Task {} action={} instance={} nodeKey={}",
            taskId, action, instance.getId(), task.getNodeKey());
    }

    // ---------------------------------------------------------------
    // Authorization helpers
    // ---------------------------------------------------------------

    /**
     * Returns the current user context, throwing 401 if missing.
     */
    public UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    /**
     * Asserts that the calling user is allowed to act on the given task.
     * Bypassed for super_admin (consistent with {@code lumen-org/EmployeeController}).
     */
    public void assertCanActOnTask(WfTask task, UserContext ctx) {
        Set<String> userRoles = ctx.getRoles() == null ? Collections.emptySet() : ctx.getRoles();
        if (userRoles.contains(SUPER_ADMIN_ROLE)) {
            return;
        }
        Long userId = ctx.getUserId();
        boolean isAssignee = userId != null && userId.equals(task.getAssignee());
        boolean isCandidateUser = task.getCandidateUsers() != null
            && userId != null
            && task.getCandidateUsers().contains(userId);
        boolean isCandidateRole = task.getCandidateRoles() != null
            && !Collections.disjoint(task.getCandidateRoles(), userRoles);
        if (!isAssignee && !isCandidateUser && !isCandidateRole) {
            throw new ServiceException(403, "Not authorized to act on this task");
        }
    }

    /**
     * Asserts that the calling user is allowed to cancel the given instance.
     * Bypassed for super_admin.
     */
    public void assertCanCancel(WfInstance instance, UserContext ctx) {
        Set<String> userRoles = ctx.getRoles() == null ? Collections.emptySet() : ctx.getRoles();
        if (userRoles.contains(SUPER_ADMIN_ROLE)) {
            return;
        }
        Long userId = ctx.getUserId();
        if (userId == null || !userId.equals(instance.getStarter())) {
            throw new ServiceException(403, "Only starter can cancel instance");
        }
    }

    // ---------------------------------------------------------------
    // Action handlers
    // ---------------------------------------------------------------

    private void advanceToNext(WfDefinition def, WfInstance instance) {
        UserTaskNode next = findNextUserTask(def.getBpmnXml(), instance.getCurrentNodeKey());
        if (next == null) {
            // Reached end — close instance
            instance.setStatus(INSTANCE_STATUS_COMPLETED);
            instance.setEndTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
            log.info("Workflow instance completed id={}", instance.getId());
            return;
        }
        createNextTask(instance, next);
    }

    private void handleTransfer(WfTask source, Map<String, Object> params) {
        if (params == null || !params.containsKey("toUserId")) {
            throw new ServiceException(400, "transfer requires toUserId");
        }
        Long toUserId = ((Number) params.get("toUserId")).longValue();
        if (toUserId == null || toUserId <= 0) {
            throw new ServiceException(400, "transfer toUserId must be positive");
        }
        // TODO: cross-tenant validation needs auth-service integration; out of scope for P3.
        WfTask newTask = cloneForNewAssignee(source, toUserId);
        newTask.setStatus(TASK_STATUS_TODO);
        taskMapper.insert(newTask);
    }

    private void handleAddSign(WfTask source, Map<String, Object> params) {
        if (params == null || !params.containsKey("userIds")) {
            throw new ServiceException(400, "addSign requires userIds");
        }
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) params.get("userIds");
        if (raw == null || raw.isEmpty()) {
            throw new ServiceException(400, "addSign userIds must not be empty");
        }
        // Dedupe via LinkedHashSet to preserve order.
        Set<Long> userIds = new LinkedHashSet<>();
        for (Number n : raw) {
            if (n != null) {
                userIds.add(n.longValue());
            }
        }
        if (userIds.isEmpty()) {
            throw new ServiceException(400, "addSign userIds must not be empty");
        }
        for (Long uid : userIds) {
            WfTask t = cloneForNewAssignee(source, uid);
            t.setStatus(TASK_STATUS_TODO);
            taskMapper.insert(t);
        }
    }

    private void handleReject(WfDefinition def, WfInstance instance, WfTask source, Map<String, Object> params) {
        if (params == null || !params.containsKey("targetNodeKey")) {
            throw new ServiceException(400, "reject requires targetNodeKey");
        }
        String targetNodeKey = (String) params.get("targetNodeKey");
        UserTaskNode target = extractUserTaskById(def.getBpmnXml(), targetNodeKey);
        if (target == null) {
            throw new ServiceException(404, "Reject target node not found in BPMN: " + targetNodeKey);
        }
        instance.setCurrentNodeKey(target.id);
        instanceMapper.updateById(instance);

        WfTask newTask = new WfTask();
        newTask.setInstanceId(instance.getId());
        newTask.setNodeKey(target.id);
        newTask.setNodeName(target.name);
        newTask.setStatus(TASK_STATUS_TODO);
        taskMapper.insert(newTask);
    }

    private WfTask cloneForNewAssignee(WfTask source, Long newAssignee) {
        WfTask t = new WfTask();
        t.setInstanceId(source.getInstanceId());
        t.setNodeKey(source.getNodeKey());
        t.setNodeName(source.getNodeName());
        t.setAssignee(newAssignee);
        t.setCandidateUsers(source.getCandidateUsers());
        t.setCandidateRoles(source.getCandidateRoles());
        return t;
    }

    private void createNextTask(WfInstance instance, UserTaskNode next) {
        WfTask t = new WfTask();
        t.setInstanceId(instance.getId());
        t.setNodeKey(next.id);
        t.setNodeName(next.name);
        t.setStatus(TASK_STATUS_TODO);
        taskMapper.insert(t);

        instance.setCurrentNodeKey(next.id);
        instanceMapper.updateById(instance);
    }

    // ---------------------------------------------------------------
    // BPMN helpers (留 TODO: full BPMN 2.0 parser)
    // ---------------------------------------------------------------

    /** Lightweight value object holding an extracted userTask. */
    public static final class UserTaskNode {
        public final String id;
        public final String name;
        public final int order;

        public UserTaskNode(String id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }

    private UserTaskNode extractFirstUserTask(String bpmnXml) {
        List<UserTaskNode> all = collectUserTasks(bpmnXml);
        return all.isEmpty() ? null : all.get(0);
    }

    private UserTaskNode extractUserTaskById(String bpmnXml, String nodeId) {
        if (bpmnXml == null || nodeId == null) return null;
        return collectUserTasks(bpmnXml).stream()
            .filter(n -> nodeId.equals(n.id))
            .findFirst().orElse(null);
    }

    /**
     * Placeholder next-node resolution: returns the next userTask in document order
     * after {@code currentNodeKey}. A real BPMN engine must follow sequenceFlow edges
     * and respect gateways — that's the TODO.
     */
    private UserTaskNode findNextUserTask(String bpmnXml, String currentNodeKey) {
        List<UserTaskNode> nodes = collectUserTasks(bpmnXml);
        // Defensive sanity check — surface an error if the persisted currentNodeKey
        // is not present in the BPMN (would otherwise silently return null and close
        // the instance as completed).
        if (currentNodeKey != null && !currentNodeKey.isEmpty()) {
            boolean found = false;
            for (UserTaskNode n : nodes) {
                if (currentNodeKey.equals(n.id)) { found = true; break; }
            }
            if (!found) {
                throw new ServiceException(500,
                    "currentNodeKey=" + currentNodeKey + " not present in BPMN userTask list");
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (currentNodeKey.equals(nodes.get(i).id)) {
                return (i + 1 < nodes.size()) ? nodes.get(i + 1) : null;
            }
        }
        return null;
    }

    /**
     * Extracts all userTask nodes from the BPMN XML, in document order, tolerating
     * {@code id} / {@code name} attribute order (real BPMN allows either).
     */
    public List<UserTaskNode> collectUserTasks(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isEmpty()) return List.of();
        Matcher m = USER_TASK_PATTERN.matcher(bpmnXml);
        List<UserTaskNode> out = new java.util.ArrayList<>();
        int order = 0;
        while (m.find()) {
            String attrs = m.group(1);
            String id = matchAttr(ID_ATTR, attrs);
            String name = matchAttr(NAME_ATTR, attrs);
            out.add(new UserTaskNode(id, name, order++));
        }
        return out;
    }

    private static String matchAttr(Pattern p, String attrs) {
        if (attrs == null) return null;
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    // ---------------------------------------------------------------
    // Internal helpers exposed for InstanceService (cancel)
    // ---------------------------------------------------------------

    /** Exposed so InstanceService.cancel can re-use the status constant without coupling. */
    public void closeInstanceCancelled(WfInstance instance, String reason) {
        instance.setStatus(INSTANCE_STATUS_CANCELLED);
        instance.setEndTime(LocalDateTime.now());
        instanceMapper.updateById(instance);
        // Also mark any open tasks as done
        List<WfTask> open = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
            .eq(WfTask::getInstanceId, instance.getId())
            .eq(WfTask::getStatus, TASK_STATUS_TODO));
        LocalDateTime now = LocalDateTime.now();
        for (WfTask t : open) {
            t.setStatus(TASK_STATUS_DONE);
            t.setCompleteTime(now);
            t.setComment(reason);
            taskMapper.updateById(t);
        }
    }
}
