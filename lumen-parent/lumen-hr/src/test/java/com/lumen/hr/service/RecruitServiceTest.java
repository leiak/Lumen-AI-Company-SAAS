package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.AddCandidateRequest;
import com.lumen.hr.dto.CreateJobRequest;
import com.lumen.hr.dto.SendOfferRequest;
import com.lumen.hr.entity.HrOffer;
import com.lumen.hr.entity.HrRecruitCandidate;
import com.lumen.hr.entity.HrRecruitJob;
import com.lumen.hr.mapper.HrOfferMapper;
import com.lumen.hr.mapper.HrRecruitCandidateMapper;
import com.lumen.hr.mapper.HrRecruitJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RecruitService coverage: JD CRUD, candidate stage progression, Offer dispatch,
 * tenant guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecruitServiceTest {

    @Mock private HrRecruitJobMapper jobMapper;
    @Mock private HrRecruitCandidateMapper candidateMapper;
    @Mock private HrOfferMapper offerMapper;

    @InjectMocks private RecruitService recruitService;

    private static final long TID = 1L;
    private static final long OTHER_TID = 2L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).userName("recruiter")
            .roles(new HashSet<>(Set.of("hr_admin"))).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private HrRecruitJob stubJob(long id, long tenantId) {
        HrRecruitJob j = new HrRecruitJob();
        j.setId(id);
        j.setTenantId(tenantId);
        j.setTitle("Java 工程师");
        j.setStatus(0);
        return j;
    }

    // ---------------------------------------------------------------
    // Job
    // ---------------------------------------------------------------

    @Test
    void createJob_persistsWithDraftStatus() {
        when(jobMapper.insert(any(HrRecruitJob.class))).thenAnswer(inv -> {
            HrRecruitJob j = inv.getArgument(0);
            j.setId(1L);
            return 1;
        });
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Java");
        req.setDeptId(1L);
        req.setPostId(1L);
        req.setHeadcount(2);

        HrRecruitJob out = recruitService.createJob(req);
        assertEquals(1L, out.getId());
        assertEquals(RecruitService.JOB_STATUS_DRAFT, out.getStatus());
        assertEquals(TID, out.getTenantId());
        assertEquals(2, out.getHeadcount());
    }

    @Test
    void createJob_missingTitle_throws400() {
        CreateJobRequest req = new CreateJobRequest();
        req.setDeptId(1L);
        req.setPostId(1L);
        // title is blank → @Valid on controller; service layer returns 400 only if
        // somehow invoked directly with a null title.
        req.setTitle("   ");
        // Note: blank title reaches service; service should still 400.
        ServiceException ex = assertThrows(ServiceException.class,
            () -> recruitService.createJob(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void publishJob_flipsStatusToPublished() {
        when(jobMapper.selectById(1L)).thenReturn(stubJob(1L, TID));
        when(jobMapper.updateById(any(HrRecruitJob.class))).thenAnswer(inv -> 1);

        HrRecruitJob out = recruitService.publishJob(1L);
        assertEquals(RecruitService.JOB_STATUS_PUBLISHED, out.getStatus());
        assertNotNull(out.getPublishedAt());
    }

    @Test
    void getJob_crossTenant_returns404() {
        when(jobMapper.selectById(1L)).thenReturn(stubJob(1L, OTHER_TID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> recruitService.getJob(1L));
        assertEquals(404, ex.getCode());
    }

    // ---------------------------------------------------------------
    // Candidate
    // ---------------------------------------------------------------

    @Test
    void addCandidate_insertsWithStageZero() {
        when(jobMapper.selectById(10L)).thenReturn(stubJob(10L, TID));
        when(candidateMapper.insert(any(HrRecruitCandidate.class))).thenAnswer(inv -> {
            HrRecruitCandidate c = inv.getArgument(0);
            c.setId(101L);
            return 1;
        });

        AddCandidateRequest req = new AddCandidateRequest();
        req.setJobId(10L);
        req.setName("张候选");
        req.setMobileEnc("13800138000");

        HrRecruitCandidate out = recruitService.addCandidate(req);
        assertEquals(101L, out.getId());
        assertEquals(0, out.getStage());
        assertEquals("张候选", out.getName());
    }

    @Test
    void addCandidate_jobMissing_throws404() {
        when(jobMapper.selectById(10L)).thenReturn(null);
        AddCandidateRequest req = new AddCandidateRequest();
        req.setJobId(10L);
        req.setName("x");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> recruitService.addCandidate(req));
        assertEquals(404, ex.getCode());
        verify(candidateMapper, never()).insert(any());
    }

    @Test
    void advanceStage_invalidStage_throws400() {
        when(candidateMapper.selectById(101L)).thenAnswer(inv -> {
            HrRecruitCandidate c = new HrRecruitCandidate();
            c.setId(101L);
            c.setTenantId(TID);
            c.setStage(0);
            return c;
        });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> recruitService.advanceStage(101L, 99));
        assertEquals(400, ex.getCode());
        verify(candidateMapper, never()).updateById(any());
    }

    // ---------------------------------------------------------------
    // Offer
    // ---------------------------------------------------------------

    @Test
    void sendOffer_createsOfferAndAdvancesCandidate() {
        HrRecruitCandidate c = new HrRecruitCandidate();
        c.setId(101L);
        c.setTenantId(TID);
        c.setStage(0);
        when(candidateMapper.selectById(101L)).thenReturn(c);
        when(offerMapper.insert(any(HrOffer.class))).thenAnswer(inv -> {
            HrOffer o = inv.getArgument(0);
            o.setId(9001L);
            return 1;
        });
        when(candidateMapper.updateById(any(HrRecruitCandidate.class))).thenAnswer(inv -> 1);

        SendOfferRequest req = new SendOfferRequest();
        req.setCandidateId(101L);
        req.setSalary(new BigDecimal("25000.00"));
        req.setStartDate(LocalDate.now().plusDays(14));

        HrOffer out = recruitService.sendOffer(req);
        assertEquals(9001L, out.getId());
        assertEquals(RecruitService.OFFER_STATUS_SENT, out.getStatus());
        assertNotNull(out.getSentAt());

        // Candidate promoted to OFFER stage.
        assertEquals(RecruitService.CANDIDATE_STAGE_OFFER, c.getStage());
        verify(candidateMapper).updateById(c);
    }

    @Test
    void sendOffer_candidateCrossTenant_throws404() {
        HrRecruitCandidate c = new HrRecruitCandidate();
        c.setId(101L);
        c.setTenantId(OTHER_TID);
        when(candidateMapper.selectById(101L)).thenReturn(c);

        SendOfferRequest req = new SendOfferRequest();
        req.setCandidateId(101L);
        req.setSalary(new BigDecimal("1000"));
        req.setStartDate(LocalDate.now());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> recruitService.sendOffer(req));
        assertEquals(404, ex.getCode());
        verify(offerMapper, never()).insert(any());
    }
}
