package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Opportunity;
import com.lumen.sales.mapper.CustomerMapper;
import com.lumen.sales.mapper.OpportunityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OpportunityService 状态机: qualification→proposal→negotiation→won/lost。
 * 单向,不能跳级,不能从 won/lost 再转。
 */
@ExtendWith(MockitoExtension.class)
class OpportunityServiceTest {

    @Mock private OpportunityMapper opportunityMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerService customerService;

    @InjectMocks private OpportunityService opportunityService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long OPP_ID = 99L;
    private static final long CUSTOMER_ID = 88L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("sales")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Opportunity stub(String stage, String status) {
        Opportunity o = new Opportunity();
        o.setId(OPP_ID);
        o.setName("Big deal");
        o.setCustomerId(CUSTOMER_ID);
        o.setStage(stage);
        o.setStatus(status);
        o.setAmount(new BigDecimal("100000"));
        o.setProbability(50);
        o.setExpectedCloseDate(LocalDate.now().plusDays(30));
        o.setOwnerUserId(UID);
        o.setTenantId(TENANT);
        return o;
    }

    @Test
    void assertStageTransition_legalPath_doesNotThrow() {
        assertDoesNotThrow(() -> opportunityService.assertStageTransition(
            OpportunityService.STAGE_QUALIFICATION, OpportunityService.STAGE_PROPOSAL));
        assertDoesNotThrow(() -> opportunityService.assertStageTransition(
            OpportunityService.STAGE_PROPOSAL, OpportunityService.STAGE_NEGOTIATION));
        assertDoesNotThrow(() -> opportunityService.assertStageTransition(
            OpportunityService.STAGE_NEGOTIATION, OpportunityService.STAGE_WON));
        assertDoesNotThrow(() -> opportunityService.assertStageTransition(
            OpportunityService.STAGE_NEGOTIATION, OpportunityService.STAGE_LOST));
    }

    @Test
    void assertStageTransition_skippingStates_throws409() {
        // qualification -> won (skip proposal + negotiation)
        ServiceException ex = assertThrows(ServiceException.class, () ->
            opportunityService.assertStageTransition(
                OpportunityService.STAGE_QUALIFICATION, OpportunityService.STAGE_WON));
        assertEquals(409, ex.getCode());
    }

    @Test
    void assertStageTransition_backwards_throws409() {
        // proposal -> qualification (backwards)
        ServiceException ex = assertThrows(ServiceException.class, () ->
            opportunityService.assertStageTransition(
                OpportunityService.STAGE_PROPOSAL, OpportunityService.STAGE_QUALIFICATION));
        assertEquals(409, ex.getCode());
    }

    @Test
    void assertStageTransition_fromWon_throws409() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            opportunityService.assertStageTransition(
                OpportunityService.STAGE_WON, OpportunityService.STAGE_PROPOSAL));
        assertEquals(409, ex.getCode());
    }

    @Test
    void assertStageTransition_fromLost_throws409() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            opportunityService.assertStageTransition(
                OpportunityService.STAGE_LOST, OpportunityService.STAGE_QUALIFICATION));
        assertEquals(409, ex.getCode());
    }

    @Test
    void stageUpdate_negotiationToWon_setsStatusWon() {
        Opportunity o = stub(OpportunityService.STAGE_NEGOTIATION, OpportunityService.STATUS_OPEN);
        when(opportunityMapper.selectById(OPP_ID)).thenReturn(o);
        when(opportunityMapper.updateById(any(Opportunity.class))).thenAnswer(inv -> 1);

        Opportunity out = opportunityService.stageUpdate(OPP_ID, OpportunityService.STAGE_WON, null);
        assertEquals(OpportunityService.STAGE_WON, out.getStage());
        assertEquals(OpportunityService.STATUS_WON, out.getStatus());
    }

    @Test
    void markWon_requiresNegotiation() {
        Opportunity o = stub(OpportunityService.STAGE_PROPOSAL, OpportunityService.STATUS_OPEN);
        when(opportunityMapper.selectById(OPP_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> opportunityService.markWon(OPP_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void markWon_legal_flipsToWon() {
        Opportunity o = stub(OpportunityService.STAGE_NEGOTIATION, OpportunityService.STATUS_OPEN);
        when(opportunityMapper.selectById(OPP_ID)).thenReturn(o);
        when(opportunityMapper.updateById(any(Opportunity.class))).thenAnswer(inv -> 1);
        Opportunity out = opportunityService.markWon(OPP_ID);
        assertEquals(OpportunityService.STAGE_WON, out.getStage());
        assertEquals(OpportunityService.STATUS_WON, out.getStatus());
    }

    @Test
    void markLost_requiresStatusOpen() {
        Opportunity o = stub(OpportunityService.STAGE_WON, OpportunityService.STATUS_WON);
        when(opportunityMapper.selectById(OPP_ID)).thenReturn(o);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> opportunityService.markLost(OPP_ID, "reason"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void markLost_legal_setsStatusLost() {
        Opportunity o = stub(OpportunityService.STAGE_NEGOTIATION, OpportunityService.STATUS_OPEN);
        when(opportunityMapper.selectById(OPP_ID)).thenReturn(o);
        when(opportunityMapper.updateById(any(Opportunity.class))).thenAnswer(inv -> 1);
        Opportunity out = opportunityService.markLost(OPP_ID, "price too high");
        assertEquals(OpportunityService.STAGE_LOST, out.getStage());
        assertEquals(OpportunityService.STATUS_LOST, out.getStatus());
        assertEquals("price too high", out.getCloseReason());
    }

    @Test
    void save_invalidProbability_throws400() {
        Opportunity req = new Opportunity();
        req.setName("deal");
        req.setCustomerId(CUSTOMER_ID);
        req.setAmount(new BigDecimal("100"));
        req.setProbability(150);
        req.setExpectedCloseDate(LocalDate.now());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> opportunityService.save(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void save_missingCustomerId_throws400() {
        Opportunity req = new Opportunity();
        req.setName("deal");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> opportunityService.save(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void allowedStages_containsFive() {
        assertEquals(5, opportunityService.allowedStages().size());
    }
}