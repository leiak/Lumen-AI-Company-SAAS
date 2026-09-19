package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.entity.SignTask;
import com.lumen.contract.mapper.SignTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests the sign-task workflow:
 *   - markSigned flips individual task + triggers checkStatus → contract.status → signed
 *   - createTask requires contract.status in approved/pending_approval/signing
 *   - myTasks uses ctx userId (never trust request userId)
 */
@ExtendWith(MockitoExtension.class)
class SignServiceTest {

    @Mock private SignTaskMapper signTaskMapper;
    @Mock private ContractService contractService;
    @Mock private ChangeLogService changeLogService;

    @InjectMocks private SignService signService;

    private static final long UID = 100L;
    private static final long OTHER_UID = 200L;
    private static final long TENANT = 1L;
    private static final long CONTRACT_ID = 99L;
    private static final long TASK_ID = 50L;

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

    private Contract stubContract(String status) {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setStatus(status);
        c.setTenantId(TENANT);
        c.setDrafterId(UID);
        return c;
    }

    private SignTask stubSignTask(long id, String status) {
        SignTask t = new SignTask();
        t.setId(id);
        t.setContractId(CONTRACT_ID);
        t.setSignerUserId(UID);
        t.setSignerRole("party_a");
        t.setSignMethod("electronic");
        t.setStatus(status);
        t.setTenantId(TENANT);
        return t;
    }

    // ---------------------------------------------------------------
    // createTask
    // ---------------------------------------------------------------

    @Test
    void createTask_legalStatus_flipsContractToSigning() {
        when(contractService.get(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_APPROVED));
        when(signTaskMapper.insert(any(SignTask.class))).thenAnswer(inv -> {
            SignTask arg = inv.getArgument(0);
            arg.setId(TASK_ID);
            return 1;
        });

        SignTask t = signService.createTask(CONTRACT_ID, OTHER_UID,
            "party_a", "electronic", "qiyuesuo");
        assertEquals(SignService.STATUS_PENDING, t.getStatus());
        verify(contractService).markSigning(CONTRACT_ID);
    }

    @Test
    void createTask_fromDrafting_throws409() {
        when(contractService.get(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_DRAFTING));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> signService.createTask(CONTRACT_ID, OTHER_UID,
                "party_a", "electronic", "qiyuesuo"));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // markSigned — chain trigger to contract.status=signed
    // ---------------------------------------------------------------

    @Test
    void markSigned_lastTask_flipsContractToSigned() {
        SignTask t = stubSignTask(TASK_ID, SignService.STATUS_PENDING);
        when(signTaskMapper.selectById(TASK_ID)).thenReturn(t);
        when(signTaskMapper.findByContract(eq(CONTRACT_ID), eq(TENANT)))
            .thenReturn(List.of(t));

        SignTask result = signService.markSigned(TASK_ID, "EXT-1", 100L);
        assertEquals(SignService.STATUS_SIGNED, result.getStatus());
        assertNotNull(result.getSignedAt());

        // Chain trigger: contractService.markSigned must be called
        verify(contractService).markSigned(CONTRACT_ID);
        // change_log auto-recorded
        verify(changeLogService).record(eq(CONTRACT_ID), eq("sign"),
            any(), any(), eq("signer signed"));
    }

    @Test
    void markSigned_notLastTask_doesNotFlipContract() {
        SignTask t1 = stubSignTask(TASK_ID, SignService.STATUS_PENDING);
        SignTask t2 = stubSignTask(TASK_ID + 1, SignService.STATUS_PENDING);
        when(signTaskMapper.selectById(TASK_ID)).thenReturn(t1);
        when(signTaskMapper.findByContract(eq(CONTRACT_ID), eq(TENANT)))
            .thenReturn(List.of(t1, t2));

        signService.markSigned(TASK_ID, "EXT-1", null);

        // checkStatus runs but allSigned=false → markSigned NOT called
        verify(contractService, never()).markSigned(any());
    }

    @Test
    void markSigned_alreadySigned_throws409() {
        SignTask t = stubSignTask(TASK_ID, SignService.STATUS_SIGNED);
        when(signTaskMapper.selectById(TASK_ID)).thenReturn(t);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> signService.markSigned(TASK_ID, "EXT-1", null));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // markRejected
    // ---------------------------------------------------------------

    @Test
    void markRejected_recordsChangeLogWithReason() {
        SignTask t = stubSignTask(TASK_ID, SignService.STATUS_PENDING);
        when(signTaskMapper.selectById(TASK_ID)).thenReturn(t);

        SignTask result = signService.markRejected(TASK_ID, "条款不认同");
        assertEquals(SignService.STATUS_REJECTED, result.getStatus());

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(changeLogService).record(eq(CONTRACT_ID), eq("sign"),
            any(), any(), eq("signer rejected"));
    }

    // ---------------------------------------------------------------
    // myTasks — uses ctx userId (NEVER request param)
    // ---------------------------------------------------------------

    @Test
    void myTasks_usesContextUserIdNotRequestParam() {
        when(signTaskMapper.findBySigner(eq(UID), eq(SignService.STATUS_PENDING), eq(TENANT)))
            .thenReturn(List.of(stubSignTask(TASK_ID, SignService.STATUS_PENDING)));

        List<SignTask> mine = signService.myTasks();
        assertEquals(1, mine.size());
        // Verify the mapper was called with the CTX user id (UID), NOT some injected id.
        verify(signTaskMapper).findBySigner(eq(UID), eq(SignService.STATUS_PENDING), eq(TENANT));
    }

    // ---------------------------------------------------------------
    // createTask — defaults provider
    // ---------------------------------------------------------------

    @Test
    void createTask_defaultsProviderToQiyuesuo() {
        when(contractService.get(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_APPROVED));
        when(signTaskMapper.insert(any(SignTask.class))).thenAnswer(inv -> {
            SignTask arg = inv.getArgument(0);
            arg.setId(TASK_ID);
            return 1;
        });

        SignTask t = signService.createTask(CONTRACT_ID, OTHER_UID,
            "party_b", "electronic", null);
        assertEquals("qiyuesuo", t.getSignProvider());
    }
}
