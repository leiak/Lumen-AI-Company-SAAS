package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.ChangeLog;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.mapper.ChangeLogMapper;
import com.lumen.contract.mapper.ClauseMapper;
import com.lumen.contract.mapper.ContractMapper;
import com.lumen.contract.mapper.SignTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests the contract state machine, cross-tenant guard, and change_log auto-recording.
 * Goal: state machine asserts, change_log auto-recording, cross-tenant 404.
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock private ContractMapper contractMapper;
    @Mock private SignTaskMapper signTaskMapper;
    @Mock private ChangeLogService changeLogService;
    @Mock private TemplateService templateService;
    @Mock private ClauseService clauseService;
    @Mock private ChangeLogMapper changeLogMapper;
    @Mock private ClauseMapper clauseMapper;

    @InjectMocks private ContractService contractService;

    private static final long UID = 100L;
    private static final long OTHER_UID = 200L;
    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final long CONTRACT_ID = 99L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Contract stubContract(String status, long drafterId, long tenantId) {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setContractNo("C-001");
        c.setTitle("Sales contract");
        c.setStatus(status);
        c.setDrafterId(drafterId);
        c.setTenantId(tenantId);
        return c;
    }

    // ---------------------------------------------------------------
    // assertStatusTransition
    // ---------------------------------------------------------------

    @Test
    void assertStatusTransition_legalPath_doesNotThrow() {
        // drafting -> pending_approval (legal)
        assertDoesNotThrow(() ->
            contractService.assertStatusTransition(
                ContractService.STATUS_DRAFTING, ContractService.STATUS_PENDING_APPROVAL));
        // approved -> signing (legal)
        assertDoesNotThrow(() ->
            contractService.assertStatusTransition(
                ContractService.STATUS_APPROVED, ContractService.STATUS_SIGNING));
        // signed -> fulfilling (legal)
        assertDoesNotThrow(() ->
            contractService.assertStatusTransition(
                ContractService.STATUS_SIGNED, ContractService.STATUS_FULFILLING));
        // signed -> archived (legal)
        assertDoesNotThrow(() ->
            contractService.assertStatusTransition(
                ContractService.STATUS_SIGNED, ContractService.STATUS_ARCHIVED));
    }

    @Test
    void assertStatusTransition_skippingStates_throws409() {
        // drafting -> approved (skip pending_approval)
        ServiceException ex = assertThrows(ServiceException.class, () ->
            contractService.assertStatusTransition(
                ContractService.STATUS_DRAFTING, ContractService.STATUS_APPROVED));
        assertEquals(409, ex.getCode());
    }

    @Test
    void assertStatusTransition_backwards_throws409() {
        // pending_approval -> archived (not allowed; must go via approved/signing/signed)
        ServiceException ex = assertThrows(ServiceException.class, () ->
            contractService.assertStatusTransition(
                ContractService.STATUS_PENDING_APPROVAL, ContractService.STATUS_ARCHIVED));
        assertEquals(409, ex.getCode());
    }

    @Test
    void assertStatusTransition_fromArchived_throws409() {
        // archived is terminal — no transitions out
        ServiceException ex = assertThrows(ServiceException.class, () ->
            contractService.assertStatusTransition(
                ContractService.STATUS_ARCHIVED, ContractService.STATUS_TERMINATED));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // get() — cross-tenant 404
    // ---------------------------------------------------------------

    @Test
    void get_sameTenant_returnsContract() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        Contract got = contractService.get(CONTRACT_ID);
        assertEquals(CONTRACT_ID, got.getId());
    }

    @Test
    void get_crossTenant_returns404() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, OTHER_TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.get(CONTRACT_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void get_superAdminCanAccessAnyTenant() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, OTHER_TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        UserContextHolder.set(UserContext.builder()
            .userId(999L).tenantId(TENANT).userName("root")
            .roles(Set.of("super_admin")).build());
        Contract got = contractService.get(CONTRACT_ID);
        assertEquals(CONTRACT_ID, got.getId());
    }

    // ---------------------------------------------------------------
    // save — change_log auto-record
    // ---------------------------------------------------------------

    @Test
    void save_createsContractAndRecordsChangeLog() {
        Contract req = new Contract();
        req.setContractNo("C-002");
        req.setTitle("Sales");
        req.setStatus("drafting");

        when(contractMapper.insert(any(Contract.class))).thenAnswer(inv -> {
            Contract arg = inv.getArgument(0);
            arg.setId(CONTRACT_ID);
            return 1;
        });

        Contract saved = contractService.save(req);
        assertEquals(CONTRACT_ID, saved.getId());

        // change_log MUST be recorded
        ArgumentCaptor<String> changeTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> contractIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(changeLogService).record(
            contractIdCaptor.capture(), changeTypeCaptor.capture(),
            eq(null), any(Map.class), eq("created"));
        assertEquals(CONTRACT_ID, contractIdCaptor.getValue());
        assertEquals("draft", changeTypeCaptor.getValue());
    }

    @Test
    void save_duplicateContractNo_throws409() {
        Contract req = new Contract();
        req.setContractNo("C-DUP");
        req.setTitle("dup");
        when(contractMapper.insert(any(Contract.class)))
            .thenThrow(new DuplicateKeyException("uk_tenant_contract_no"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.save(req));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // submit — drafting -> pending_approval
    // ---------------------------------------------------------------

    @Test
    void submit_legalTransition_recordsChangeLog() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);

        Contract result = contractService.submit(CONTRACT_ID,
            List.of(10L, 20L), List.of("party_a", "party_b"));
        assertEquals(ContractService.STATUS_PENDING_APPROVAL, result.getStatus());

        // change_log MUST be recorded for the status transition
        verify(changeLogService).record(eq(CONTRACT_ID), eq("draft"),
            any(Map.class), any(Map.class), eq("submitted for approval"));
    }

    @Test
    void submit_wrongUser_throws403() {
        // Current user is UID; contract drafter is OTHER_UID.
        Contract c = stubContract(ContractService.STATUS_DRAFTING, OTHER_UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        // Strip admin role to test drafter-only path.
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of()).build());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.submit(CONTRACT_ID, List.of(10L), List.of("party_a")));
        assertEquals(403, ex.getCode());
    }

    // ---------------------------------------------------------------
    // approve — pending_approval -> approved
    // ---------------------------------------------------------------

    @Test
    void approve_legalTransition_recordsChangeLog() {
        Contract c = stubContract(ContractService.STATUS_PENDING_APPROVAL, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);

        Contract result = contractService.approve(CONTRACT_ID, "ok");
        assertEquals(ContractService.STATUS_APPROVED, result.getStatus());
        verify(changeLogService).record(eq(CONTRACT_ID), eq("approve"),
            any(Map.class), any(Map.class), eq("ok"));
    }

    @Test
    void approve_fromDrafting_throws409() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.approve(CONTRACT_ID, null));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // terminate — writes reason to change_log
    // ---------------------------------------------------------------

    @Test
    void terminate_legalTransition_recordsReasonInChangeLog() {
        Contract c = stubContract(ContractService.STATUS_APPROVED, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);

        Contract result = contractService.terminate(CONTRACT_ID, "双方协商解约");
        assertEquals(ContractService.STATUS_TERMINATED, result.getStatus());

        verify(changeLogService).record(eq(CONTRACT_ID), eq("terminate"),
            any(Map.class), any(Map.class), eq("双方协商解约"));
    }

    // ---------------------------------------------------------------
    // archive — only from signed/fulfilling/expired/terminated
    // ---------------------------------------------------------------

    @Test
    void archive_fromSigned_legal() {
        Contract c = stubContract(ContractService.STATUS_SIGNED, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        Contract result = contractService.archive(CONTRACT_ID);
        assertEquals(ContractService.STATUS_ARCHIVED, result.getStatus());
    }

    @Test
    void archive_fromDrafting_throws409() {
        Contract c = stubContract(ContractService.STATUS_DRAFTING, UID, TENANT);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.archive(CONTRACT_ID));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // draft — auto-seed clause from template
    // ---------------------------------------------------------------

    @Test
    void draft_callsTemplateRenderAndSeedsClause() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        TemplateService.Rendered rendered = new TemplateService.Rendered(
            7L, "Sales Template", "sales", List.of("name"), vars);

        when(templateService.renderVariables(eq(7L), eq(vars))).thenReturn(rendered);
        when(contractMapper.insert(any(Contract.class))).thenAnswer(inv -> {
            Contract arg = inv.getArgument(0);
            arg.setId(CONTRACT_ID);
            return 1;
        });

        Contract c = contractService.draft(7L, vars);
        assertEquals(CONTRACT_ID, c.getId());
        assertEquals(ContractService.STATUS_DRAFTING, c.getStatus());
        verify(clauseService).autoSeedFromTemplate(eq(CONTRACT_ID), eq(rendered));
        verify(changeLogService).record(eq(CONTRACT_ID), eq("draft"),
            eq(null), any(Map.class), eq("drafted from template"));
    }
}
