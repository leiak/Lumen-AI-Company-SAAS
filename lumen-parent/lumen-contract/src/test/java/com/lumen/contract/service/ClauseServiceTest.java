package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Clause;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.mapper.ClauseMapper;
import com.lumen.contract.mapper.ContractMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ClauseService — bulkSave replace semantics + approved-state lock.
 */
@ExtendWith(MockitoExtension.class)
class ClauseServiceTest {

    @Mock private ClauseMapper clauseMapper;
    @Mock private ContractMapper contractMapper;

    @InjectMocks private ClauseService clauseService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
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

    private Contract stubContract(String status) {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setStatus(status);
        c.setTenantId(TENANT);
        c.setDrafterId(UID);
        return c;
    }

    private Clause stubClause(String no, String title, int orderNum) {
        Clause c = new Clause();
        c.setClauseNo(no);
        c.setTitle(title);
        c.setContent("content-" + no);
        c.setOrderNum(orderNum);
        c.setSource("manual");
        return c;
    }

    // ---------------------------------------------------------------
    // bulkSave — replace semantics in transaction
    // ---------------------------------------------------------------

    @Test
    void bulkSave_deletesOldAndInsertsNew() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_DRAFTING));

        List<Clause> newClauses = List.of(
            stubClause("C-1", "Clause 1", 0),
            stubClause("C-2", "Clause 2", 1));

        int n = clauseService.bulkSave(CONTRACT_ID, newClauses);
        assertEquals(2, n);

        // Verify delete by contract_id was issued
        verify(clauseMapper).delete(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        // Verify 2 inserts (one per new clause)
        verify(clauseMapper, times(2)).insert(any(Clause.class));
    }

    @Test
    void bulkSave_emptyList_deletesOnly() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_DRAFTING));

        int n = clauseService.bulkSave(CONTRACT_ID, List.of());
        assertEquals(0, n);
        verify(clauseMapper).delete(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        verify(clauseMapper, never()).insert(any(Clause.class));
    }

    @Test
    void bulkSave_afterApproval_throws409() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_APPROVED));

        List<Clause> newClauses = List.of(stubClause("C-1", "x", 0));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> clauseService.bulkSave(CONTRACT_ID, newClauses));
        assertEquals(409, ex.getCode());
        // No delete or insert
        verify(clauseMapper, never()).delete(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        verify(clauseMapper, never()).insert(any(Clause.class));
    }

    @Test
    void bulkSave_afterSigned_throws409() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_SIGNED));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> clauseService.bulkSave(CONTRACT_ID, List.of(stubClause("C-1", "x", 0))));
        assertEquals(409, ex.getCode());
    }

    // ---------------------------------------------------------------
    // save (single) — approved-state lock
    // ---------------------------------------------------------------

    @Test
    void save_afterApproval_throws409() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_APPROVED));

        Clause req = stubClause("C-1", "x", 0);
        req.setContractId(CONTRACT_ID);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> clauseService.save(req));
        assertEquals(409, ex.getCode());
        verify(clauseMapper, never()).insert(any());
    }

    @Test
    void save_inDrafting_persistsWithDefaults() {
        when(contractMapper.selectById(CONTRACT_ID))
            .thenReturn(stubContract(ContractService.STATUS_DRAFTING));
        when(clauseMapper.insert(any(Clause.class))).thenAnswer(inv -> {
            Clause arg = inv.getArgument(0);
            arg.setId(50L);
            return 1;
        });

        Clause req = stubClause("C-1", "x", 0);
        req.setContractId(CONTRACT_ID);
        Clause saved = clauseService.save(req);
        assertEquals(50L, saved.getId());

        ArgumentCaptor<Clause> captor = ArgumentCaptor.forClass(Clause.class);
        verify(clauseMapper).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
        assertEquals(TENANT, captor.getValue().getTenantId());
    }
}
