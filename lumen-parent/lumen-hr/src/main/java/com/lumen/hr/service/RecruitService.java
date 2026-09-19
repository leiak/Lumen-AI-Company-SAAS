package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Recruitment funnel: JD CRUD, candidate stage progression, Offer dispatch.
 *
 * <p>Stages for {@link HrRecruitCandidate}:</p>
 * <pre>
 *  0=简历筛选 1=初试 2=复试 3=终试 4=已发 Offer 5=已入职 6=已淘汰
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitService {

    public static final int JOB_STATUS_DRAFT = 0;
    public static final int JOB_STATUS_PUBLISHED = 1;
    public static final int JOB_STATUS_CLOSED = 2;
    public static final int JOB_STATUS_FILLED = 3;

    public static final int CANDIDATE_STAGE_OFFER = 4;
    public static final int CANDIDATE_STAGE_ONBOARDED = 5;
    public static final int CANDIDATE_STAGE_REJECTED = 6;

    public static final int OFFER_STATUS_DRAFT = 0;
    public static final int OFFER_STATUS_SENT = 1;
    public static final int OFFER_STATUS_ACCEPTED = 2;
    public static final int OFFER_STATUS_REJECTED = 3;
    public static final int OFFER_STATUS_REVOKED = 4;

    private final HrRecruitJobMapper jobMapper;
    private final HrRecruitCandidateMapper candidateMapper;
    private final HrOfferMapper offerMapper;

    // ---------------------------------------------------------------
    // Job (JD) CRUD
    // ---------------------------------------------------------------

    public IPage<HrRecruitJob> listJobs(int pageNum, int pageSize, String keyword) {
        requireTenant();
        var w = new LambdaQueryWrapper<HrRecruitJob>().orderByDesc(HrRecruitJob::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(HrRecruitJob::getTitle, keyword));
        }
        return jobMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public HrRecruitJob getJob(Long id) {
        requireTenant();
        HrRecruitJob j = jobMapper.selectById(id);
        if (j == null) throw new ServiceException(404, "Job not found: " + id);
        ensureSameTenant(j);
        return j;
    }

    @Transactional
    public HrRecruitJob createJob(CreateJobRequest req) {
        requireTenant();
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ServiceException(400, "title is required");
        }
        HrRecruitJob j = new HrRecruitJob();
        j.setTitle(req.getTitle());
        j.setDeptId(req.getDeptId());
        j.setPostId(req.getPostId());
        j.setHeadcount(req.getHeadcount() != null ? req.getHeadcount() : 1);
        j.setStatus(JOB_STATUS_DRAFT);
        j.setTenantId(currentTenantId());
        jobMapper.insert(j);
        log.info("Created job id={} title={}", j.getId(), j.getTitle());
        return j;
    }

    @Transactional
    public HrRecruitJob publishJob(Long id) {
        HrRecruitJob j = getJob(id);
        j.setStatus(JOB_STATUS_PUBLISHED);
        j.setPublishedAt(LocalDateTime.now());
        jobMapper.updateById(j);
        return j;
    }

    @Transactional
    public void closeJob(Long id) {
        HrRecruitJob j = getJob(id);
        j.setStatus(JOB_STATUS_CLOSED);
        jobMapper.updateById(j);
    }

    // ---------------------------------------------------------------
    // Candidate pipeline
    // ---------------------------------------------------------------

    @Transactional
    public HrRecruitCandidate addCandidate(AddCandidateRequest req) {
        requireTenant();
        // Validate parent job exists (tenant-scoped via interceptor).
        HrRecruitJob job = jobMapper.selectById(req.getJobId());
        if (job == null) throw new ServiceException(404, "Job not found: " + req.getJobId());

        HrRecruitCandidate c = new HrRecruitCandidate();
        c.setJobId(req.getJobId());
        c.setName(req.getName());
        c.setMobileEnc(req.getMobileEnc());
        c.setEmailEnc(req.getEmailEnc());
        c.setResumeUrl(req.getResumeUrl());
        c.setStage(0);
        c.setStatus(0);
        c.setTenantId(currentTenantId());
        candidateMapper.insert(c);
        log.info("Added candidate id={} name={} jobId={}", c.getId(), c.getName(), req.getJobId());
        return c;
    }

    @Transactional
    public HrRecruitCandidate advanceStage(Long candidateId, int newStage) {
        requireTenant();
        HrRecruitCandidate c = candidateMapper.selectById(candidateId);
        if (c == null) throw new ServiceException(404, "Candidate not found: " + candidateId);
        ensureSameTenant(c);
        if (newStage < 0 || newStage > CANDIDATE_STAGE_REJECTED) {
            throw new ServiceException(400, "Invalid stage: " + newStage);
        }
        c.setStage(newStage);
        candidateMapper.updateById(c);
        log.info("Candidate {} advanced to stage {}", candidateId, newStage);
        return c;
    }

    // ---------------------------------------------------------------
    // Offer dispatch
    // ---------------------------------------------------------------

    @Transactional
    public HrOffer sendOffer(SendOfferRequest req) {
        requireTenant();
        HrRecruitCandidate c = candidateMapper.selectById(req.getCandidateId());
        if (c == null) throw new ServiceException(404, "Candidate not found: " + req.getCandidateId());
        ensureSameTenant(c);

        HrOffer offer = new HrOffer();
        offer.setCandidateId(req.getCandidateId());
        offer.setSalary(req.getSalary());
        offer.setStartDate(req.getStartDate());
        offer.setStatus(OFFER_STATUS_SENT);
        offer.setSentAt(LocalDateTime.now());
        offer.setTenantId(currentTenantId());
        offerMapper.insert(offer);

        // Move candidate into OFFER stage.
        c.setStage(CANDIDATE_STAGE_OFFER);
        candidateMapper.updateById(c);

        log.info("Sent offer id={} candidateId={}", offer.getId(), req.getCandidateId());
        return offer;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void requireTenant() {
        if (UserContextHolder.get() == null || UserContextHolder.getTenantId() == null) {
            throw new ServiceException(401, "Missing tenant context");
        }
    }

    private long currentTenantId() {
        Long t = UserContextHolder.getTenantId();
        if (t == null) throw new ServiceException(401, "Missing tenant context");
        return t;
    }

    private void ensureSameTenant(HrRecruitJob j) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || j.getTenantId() == null || !tid.equals(j.getTenantId())) {
            throw new ServiceException(404, "Job not found: " + j.getId());
        }
    }

    private void ensureSameTenant(HrRecruitCandidate c) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || c.getTenantId() == null || !tid.equals(c.getTenantId())) {
            throw new ServiceException(404, "Candidate not found: " + c.getId());
        }
    }
}
