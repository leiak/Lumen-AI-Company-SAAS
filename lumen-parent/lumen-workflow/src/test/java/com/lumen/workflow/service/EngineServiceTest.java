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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the BPMN engine. All collaborators are mocked; these tests focus on
 * the state machine, BPMN parsing, and authorization.
 */
@ExtendWith(MockitoExtension.class)
class EngineServiceTest {

    @Mock private WfDefinitionMapper definitionMapper;
    @Mock private WfInstanceMapper instanceMapper;
    @Mock private WfTaskMapper taskMapper;
    @Mock private WfTaskHistoryMapper taskHistoryMapper;

    @InjectMocks private EngineService engineService;

    private static final long STARTER = 100L;
    private static final long OTHER_USER = 200L;
    private static final long TENANT = 1L;
    private static final long DEF_ID = 10L;
    private static final long INSTANCE_ID = 99L;
    private static final long TASK_ID_NODE1 = 501L;
    private static final long TASK_ID_NODE2 = 502L;

    /** Two-task linear BPMN (id-before-name, the canonical layout). */
    private static final String BPMN_TWO_TASK = "<definitions>"
        + "<process id=\"leave\">"
        + "<startEvent id=\"start\"/>"
        + "<userTask id=\"node1\" name=\"Manager Approval\"/>"
        + "<userTask id=\"node2\" name=\"HR Approval\"/>"
        + "<endEvent id=\"end\"/>"
        + "</process></definitions>";

    /** Single-task BPMN for terminal-node completion test. */
    private static final String BPMN_ONE_TASK = "<definitions>"
        + "<process id=\"single\">"
        + "<startEvent id=\"start\"/>"
        + "<userTask id=\"only\" name=\"Only Step\"/>"
        + "<endEvent id=\"end\"/>"
        + "</process></definitions>";

    /** BPMN where name comes BEFORE id — must still parse correctly. */
    private static final String BPMN_ID_AFTER_NAME = "<definitions>"
        + "<process id=\"reversed\">"
        + "<userTask name=\"First Step\" id=\"alpha\"/>"
        + "<userTask name=\"Second Step\" id=\"beta\"/>"
        + "</process></definitions>";

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(STARTER).tenantId(TENANT).userName("alice")
            .roles(Set.of()).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // Helper: persist behaviour for instanceMapper.insert — assigns id and echoes the object.
    private void stubInsertIdEcho() {
        when(instanceMapper.insert(any(WfInstance.class))).thenAnswer(inv -> {
            WfInstance arg = inv.getArgument(0);
            arg.setId(INSTANCE_ID);
            return 1;
        });
        when(taskMapper.insert(any(WfTask.class))).thenAnswer(inv -> {
            WfTask arg = inv.getArgument(0);
            if (arg.getId() == null) {
                arg.setId(System.nanoTime() & 0x7FFFFFFFL);
            }
            return 1;
        });
    }

    private WfDefinition stubDef(String key, String xml) {
        WfDefinition def = new WfDefinition();
        def.setId(DEF_ID);
        def.setDefKey(key);
        def.setBpmnXml(xml);
        def.setStatus(1);
        def.setVersion(1);
        lenient().when(definitionMapper.selectLatestByKey(key)).thenReturn(def);
        lenient().when(definitionMapper.selectById(DEF_ID)).thenReturn(def);
        return def;
    }

    private WfInstance stubInstance(String status) {
        WfInstance instance = new WfInstance();
        instance.setId(INSTANCE_ID);
        instance.setDefinitionId(DEF_ID);
        instance.setStatus(EngineService.INSTANCE_STATUS_RUNNING);
        instance.setCurrentNodeKey("node1");
        instance.setTenantId(TENANT);
        instance.setStarter(STARTER);
        if ("completed".equals(status)) instance.setStatus(EngineService.INSTANCE_STATUS_COMPLETED);
        return instance;
    }

    private WfTask stubTask(long taskId, String nodeKey, String nodeName, Long assignee) {
        WfTask t = new WfTask();
        t.setId(taskId);
        t.setInstanceId(INSTANCE_ID);
        t.setNodeKey(nodeKey);
        t.setNodeName(nodeName);
        t.setStatus(EngineService.TASK_STATUS_TODO);
        t.setAssignee(assignee);
        t.setCandidateUsers(new ArrayList<>());
        t.setCandidateRoles(new ArrayList<>());
        return t;
    }

    // ---------------------------------------------------------------------
    // startInstance — happy path & guards
    // ---------------------------------------------------------------------

    @Test
    void linearAdvance_twoTaskFlow_completesFirstAndCreatesSecond() {
        stubDef("leave", BPMN_TWO_TASK);
        when(instanceMapper.selectByBusinessKey("BIZ-1")).thenReturn(null);
        stubInsertIdEcho();

        Long id = engineService.startInstance("leave", "BIZ-1", null);
        assertEquals(INSTANCE_ID, id);

        ArgumentCaptor<WfInstance> instanceCaptor = ArgumentCaptor.forClass(WfInstance.class);
        verify(instanceMapper).insert(instanceCaptor.capture());
        assertEquals("node1", instanceCaptor.getValue().getCurrentNodeKey());

        ArgumentCaptor<WfTask> taskCaptor = ArgumentCaptor.forClass(WfTask.class);
        verify(taskMapper, atLeastOnce()).insert(taskCaptor.capture());
        WfTask first = taskCaptor.getAllValues().get(0);
        assertEquals("node1", first.getNodeKey());
        assertEquals("Manager Approval", first.getNodeName());
    }

    @Test
    void terminalNodeCompletes_instanceStatusFlippedToCompleted() {
        stubDef("single", BPMN_ONE_TASK);
        when(instanceMapper.selectByBusinessKey("BIZ-A")).thenReturn(null);
        stubInsertIdEcho();

        engineService.startInstance("single", "BIZ-A", null);

        // Now drive a done on the only task.
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("only");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask onlyTask = stubTask(TASK_ID_NODE1, "only", "Only Step", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(onlyTask);

        engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_DONE, "done", null);

        ArgumentCaptor<WfInstance> updateCaptor = ArgumentCaptor.forClass(WfInstance.class);
        verify(instanceMapper, atLeastOnce()).updateById(updateCaptor.capture());
        boolean sawCompleted = updateCaptor.getAllValues().stream()
            .anyMatch(i -> EngineService.INSTANCE_STATUS_COMPLETED == i.getStatus());
        assertTrue(sawCompleted, "instance should be marked COMPLETED on terminal node done");
    }

    @Test
    void startInstanceRejectsDuplicateBusinessKey_returns409() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance existing = new WfInstance();
        existing.setId(7L);
        existing.setBusinessKey("BIZ-DUP");
        existing.setStatus(EngineService.INSTANCE_STATUS_RUNNING);
        when(instanceMapper.selectByBusinessKey("BIZ-DUP")).thenReturn(existing);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.startInstance("leave", "BIZ-DUP", null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void startInstanceRejectsDuplicateBusinessKey_onRaceDuplicateKey() {
        stubDef("leave", BPMN_TWO_TASK);
        when(instanceMapper.selectByBusinessKey("BIZ-RACE")).thenReturn(null);
        // selectByBusinessKey returns null (race window) but the unique index trips at insert.
        when(instanceMapper.insert(any(WfInstance.class)))
            .thenThrow(new DuplicateKeyException("uk_business_key_active"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.startInstance("leave", "BIZ-RACE", null));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("Active instance exists"));
    }

    @Test
    void startInstanceRejectsMissingTenantContext_401() {
        UserContextHolder.clear(); // simulate missing context
        stubDef("leave", BPMN_TWO_TASK);
        when(instanceMapper.selectByBusinessKey("BIZ-T")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.startInstance("leave", "BIZ-T", null));
        assertEquals(401, ex.getCode());
    }

    // ---------------------------------------------------------------------
    // completeTask — authorization
    // ---------------------------------------------------------------------

    @Test
    void completeTask_unauthorizedUser_throws403() {
        WfInstance instance = stubInstance("running");
        lenient().when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask task = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", OTHER_USER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(task);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_DONE, null, null));
        assertEquals(403, ex.getCode());
    }

    @Test
    void completeTask_superAdminBypassesAuthz() {
        stubDef("single", BPMN_ONE_TASK);
        UserContextHolder.set(UserContext.builder()
            .userId(999L).tenantId(TENANT).userName("root")
            .roles(Set.of(EngineService.SUPER_ADMIN_ROLE)).build());

        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("only");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask task = stubTask(TASK_ID_NODE1, "only", "Only Step", OTHER_USER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(task);

        // Should not throw 403; should complete and close instance.
        engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_DONE, "admin override", null);
        verify(taskMapper).updateById(any(WfTask.class));
    }

    // ---------------------------------------------------------------------
    // completeTask — done advances to next node
    // ---------------------------------------------------------------------

    @Test
    void done_advancesToNextNode() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask t1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(t1);

        engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_DONE, "approved", null);

        // history row + new task created (the original task is updateById, not insert)
        verify(taskHistoryMapper).insert(any(WfTaskHistory.class));
        // the new task should be at node2
        ArgumentCaptor<WfTask> captor = ArgumentCaptor.forClass(WfTask.class);
        verify(taskMapper, atLeastOnce()).insert(captor.capture());
        boolean sawNode2 = captor.getAllValues().stream()
            .anyMatch(t -> "node2".equals(t.getNodeKey()) && t.getStatus() == EngineService.TASK_STATUS_TODO);
        assertTrue(sawNode2, "expected a new TASK_STATUS_TODO task at node2");

        ArgumentCaptor<WfInstance> instCaptor = ArgumentCaptor.forClass(WfInstance.class);
        verify(instanceMapper, atLeastOnce()).updateById(instCaptor.capture());
        boolean sawNode2Current = instCaptor.getAllValues().stream()
            .anyMatch(i -> "node2".equals(i.getCurrentNodeKey()));
        assertTrue(sawNode2Current, "instance.currentNodeKey should advance to node2");
    }

    // ---------------------------------------------------------------------
    // transfer
    // ---------------------------------------------------------------------

    @Test
    void transferCreatesNewTaskForTargetUser() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask t1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(t1);

        Map<String, Object> params = new HashMap<>();
        params.put("toUserId", OTHER_USER);

        engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_TRANSFER, "delegating", params);

        ArgumentCaptor<WfTask> captor = ArgumentCaptor.forClass(WfTask.class);
        verify(taskMapper, atLeastOnce()).insert(captor.capture());
        boolean handedOff = captor.getAllValues().stream()
            .anyMatch(t -> OTHER_USER == (t.getAssignee() == null ? -1L : t.getAssignee())
                && t.getStatus() == EngineService.TASK_STATUS_TODO
                && "node1".equals(t.getNodeKey()));
        assertTrue(handedOff, "transfer should create a TASK_STATUS_TODO clone assigned to toUserId");
    }

    @Test
    void transferWithoutToUserId_throws400() {
        stubDef("leave", BPMN_TWO_TASK);
        WfTask t1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(t1);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_TRANSFER, null,
                new HashMap<>()));
        assertEquals(400, ex.getCode());
    }

    @Test
    void transferWithNonPositiveToUserId_throws400() {
        stubDef("leave", BPMN_TWO_TASK);
        WfTask t1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(t1);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        Map<String, Object> params = new HashMap<>();
        params.put("toUserId", 0L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_TRANSFER, null, params));
        assertEquals(400, ex.getCode());
    }

    // ---------------------------------------------------------------------
    // addSign
    // ---------------------------------------------------------------------

    @Test
    void addSignCreatesMultipleTasks_andDeduplicates() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask t1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(t1);

        // Three user ids, two of them duplicate. Expect 2 task inserts (not 3).
        List<Number> userIds = new ArrayList<>();
        userIds.add(11L);
        userIds.add(22L);
        userIds.add(11L);
        Map<String, Object> params = new HashMap<>();
        params.put("userIds", userIds);

        engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_ADD_SIGN, "need help", params);

        ArgumentCaptor<WfTask> captor = ArgumentCaptor.forClass(WfTask.class);
        verify(taskMapper, atLeastOnce()).insert(captor.capture());
        long created = captor.getAllValues().stream()
            .filter(t -> t.getStatus() == EngineService.TASK_STATUS_TODO
                && ("node1".equals(t.getNodeKey())))
            .count();
        // We inserted exactly 2 tasks at node1 (deduped 11L) — the original task update
        // is not an insert, so we should see exactly 2 inserts for add-sign.
        assertEquals(2L, created, "expected 2 dedup'd add-sign tasks at node1");
    }

    // ---------------------------------------------------------------------
    // reject
    // ---------------------------------------------------------------------

    @Test
    void rejectGoesToTargetNode() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node2");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask t2 = stubTask(TASK_ID_NODE2, "node2", "HR Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE2)).thenReturn(t2);

        Map<String, Object> params = new HashMap<>();
        params.put("targetNodeKey", "node1");

        engineService.completeTask(TASK_ID_NODE2, EngineService.ACTION_REJECT, "back to manager", params);

        ArgumentCaptor<WfInstance> instCaptor = ArgumentCaptor.forClass(WfInstance.class);
        verify(instanceMapper, atLeastOnce()).updateById(instCaptor.capture());
        boolean sawNode1 = instCaptor.getAllValues().stream()
            .anyMatch(i -> "node1".equals(i.getCurrentNodeKey()));
        assertTrue(sawNode1, "reject should set instance.currentNodeKey back to target");

        ArgumentCaptor<WfTask> taskCaptor = ArgumentCaptor.forClass(WfTask.class);
        verify(taskMapper, atLeastOnce()).insert(taskCaptor.capture());
        boolean sawRejectTask = taskCaptor.getAllValues().stream()
            .anyMatch(t -> "node1".equals(t.getNodeKey())
                && t.getStatus() == EngineService.TASK_STATUS_TODO);
        assertTrue(sawRejectTask, "reject should create a new task at the target node");
    }

    @Test
    void rejectToUnknownNode_throws404() {
        stubDef("leave", BPMN_TWO_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node2");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask t2 = stubTask(TASK_ID_NODE2, "node2", "HR Approval", STARTER);
        when(taskMapper.selectById(TASK_ID_NODE2)).thenReturn(t2);

        Map<String, Object> params = new HashMap<>();
        params.put("targetNodeKey", "nonexistent");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.completeTask(TASK_ID_NODE2, EngineService.ACTION_REJECT, null, params));
        assertEquals(404, ex.getCode());
    }

    // ---------------------------------------------------------------------
    // closeInstanceCancelled (called by InstanceService.cancel)
    // ---------------------------------------------------------------------

    @Test
    void cancelMidFlowClosesInstanceAndTasks() {
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("node1");

        WfTask open1 = stubTask(TASK_ID_NODE1, "node1", "Manager Approval", STARTER);
        WfTask open2 = stubTask(TASK_ID_NODE2, "node2", "HR Approval", OTHER_USER);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(open1, open2));

        engineService.closeInstanceCancelled(instance, "user changed mind");

        assertEquals(EngineService.INSTANCE_STATUS_CANCELLED, instance.getStatus());
        assertNotNull(instance.getEndTime());
        verify(instanceMapper).updateById(instance);
        verify(taskMapper, atLeast(2)).updateById(any(WfTask.class));
    }

    // ---------------------------------------------------------------------
    // BPMN parsing
    // ---------------------------------------------------------------------

    @Test
    void bpmnWithIdAfterNameParsesCorrectly() {
        // First node: name="First Step" id="alpha" — reversed order
        List<EngineService.UserTaskNode> nodes = engineService.collectUserTasks(BPMN_ID_AFTER_NAME);
        assertEquals(2, nodes.size(), "should extract both userTask nodes");
        assertEquals("alpha", nodes.get(0).id);
        assertEquals("First Step", nodes.get(0).name);
        assertEquals("beta", nodes.get(1).id);
        assertEquals("Second Step", nodes.get(1).name);
    }

    @Test
    void bpmnWithIdBeforeNameParsesCorrectly() {
        List<EngineService.UserTaskNode> nodes = engineService.collectUserTasks(BPMN_TWO_TASK);
        assertEquals(2, nodes.size());
        assertEquals("node1", nodes.get(0).id);
        assertEquals("Manager Approval", nodes.get(0).name);
        assertEquals("node2", nodes.get(1).id);
        assertEquals("HR Approval", nodes.get(1).name);
    }

    @Test
    void bpmnWithSelfClosingTag_alsoParses() {
        String xml = "<definitions>"
            + "<userTask id=\"x\" name=\"X\"/>"
            + "<userTask id=\"y\" name=\"Y\"/>"
            + "</definitions>";
        List<EngineService.UserTaskNode> nodes = engineService.collectUserTasks(xml);
        assertEquals(2, nodes.size());
        assertEquals("x", nodes.get(0).id);
    }

    @Test
    void bpmnWithNullOrEmpty_returnsEmptyList() {
        assertTrue(engineService.collectUserTasks(null).isEmpty());
        assertTrue(engineService.collectUserTasks("").isEmpty());
        assertTrue(engineService.collectUserTasks("no tags here").isEmpty());
    }

    @Test
    void currentNodeKeyNotInBpmn_throwsClearError() {
        stubDef("single", BPMN_ONE_TASK);
        WfInstance instance = stubInstance("running");
        instance.setCurrentNodeKey("nonexistent_node");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        WfTask onlyTask = stubTask(TASK_ID_NODE1, "only", "Only Step", STARTER);
        onlyTask.setNodeKey("nonexistent_node");
        when(taskMapper.selectById(TASK_ID_NODE1)).thenReturn(onlyTask);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> engineService.completeTask(TASK_ID_NODE1, EngineService.ACTION_DONE, null, null));
        // Either 500 (BPMN mismatch) or 404/409 depending on order — both are acceptable signals.
        assertTrue(ex.getCode() == 500 || ex.getCode() == 404 || ex.getCode() == 409,
            "expected clear error code, got " + ex.getCode());
    }
}
