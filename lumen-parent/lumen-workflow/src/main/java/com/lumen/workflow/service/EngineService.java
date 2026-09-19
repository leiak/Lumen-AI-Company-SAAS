package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Pattern USER_TASK_PATTERN =
        Pattern.compile("<userTask\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"[^>]*\\bname\\s*=\\s*\"([^\"]+)\"");

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
        UserTaskNode first = extractFirstUserTask(def.getBpmnXml());
        if (first == null) {
            throw new ServiceException(400, "BPMN has no userTask: " + defKey);
        }

        Long starter = UserContextHolder.getUserId();
        Long tenantId = UserContextHolder.getTenantId();

        WfInstance instance = new WfInstance();
        instance.setDefinitionId(def.getId());
        instance.setDefKey(def.getDefKey());
        instance.setBusinessKey(businessKey);
        instance.setTenantId(tenantId != null ? tenantId : 0L);
        instance.setStatus(INSTANCE_STATUS_RUNNING);
        instance.setCurrentNodeKey(first.id);
        instance.setVariables(variables != null ? variables : new HashMap<>());
        instance.setStarter(starter != null ? starter : 0L);
        instance.setStartTime(LocalDateTime.now());
        instanceMapper.insert(instance);

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

        Long operator = UserContextHolder.getUserId();
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

        // 2) Mark the current task with the action-specific status
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
        WfTask newTask = cloneForNewAssignee(source, toUserId);
        newTask.setStatus(TASK_STATUS_TODO);
        taskMapper.insert(newTask);
    }

    private void handleAddSign(WfTask source, Map<String, Object> params) {
        if (params == null || !params.containsKey("userIds")) {
            throw new ServiceException(400, "addSign requires userIds");
        }
        @SuppressWarnings("unchecked")
        List<Number> userIds = (List<Number>) params.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            throw new ServiceException(400, "addSign userIds must not be empty");
        }
        for (Number u : userIds) {
            WfTask t = cloneForNewAssignee(source, u.longValue());
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
    private static final class UserTaskNode {
        final String id;
        final String name;
        final int order;

        UserTaskNode(String id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }

    private UserTaskNode extractFirstUserTask(String bpmnXml) {
        return collectUserTasks(bpmnXml).stream().findFirst().orElse(null);
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
        for (int i = 0; i < nodes.size(); i++) {
            if (currentNodeKey.equals(nodes.get(i).id)) {
                return (i + 1 < nodes.size()) ? nodes.get(i + 1) : null;
            }
        }
        return null;
    }

    private List<UserTaskNode> collectUserTasks(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isEmpty()) return List.of();
        Matcher m = USER_TASK_PATTERN.matcher(bpmnXml);
        int order = 0;
        java.util.List<UserTaskNode> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(new UserTaskNode(m.group(1), m.group(2), order++));
        }
        return out;
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