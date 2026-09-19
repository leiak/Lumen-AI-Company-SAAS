package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.CompleteTrainingRequest;
import com.lumen.hr.dto.CreateTrainingPlanRequest;
import com.lumen.hr.entity.HrTrainingPlan;
import com.lumen.hr.entity.HrTrainingRecord;
import com.lumen.hr.mapper.HrTrainingPlanMapper;
import com.lumen.hr.mapper.HrTrainingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Training plan + record (employee enrollment + completion) management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    public static final int PLAN_STATUS_DRAFT = 0;
    public static final int PLAN_STATUS_PUBLISHED = 1;
    public static final int PLAN_STATUS_RUNNING = 2;
    public static final int PLAN_STATUS_CLOSED = 3;
    public static final int PLAN_STATUS_CANCELLED = 4;

    private final HrTrainingPlanMapper planMapper;
    private final HrTrainingRecordMapper recordMapper;

    public IPage<HrTrainingPlan> listPlans(int pageNum, int pageSize, String keyword) {
        requireTenant();
        var w = new LambdaQueryWrapper<HrTrainingPlan>().orderByDesc(HrTrainingPlan::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(HrTrainingPlan::getName, keyword));
        }
        return planMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    @Transactional
    public HrTrainingPlan createPlan(CreateTrainingPlanRequest req) {
        requireTenant();
        if (req.getEndAt().isBefore(req.getStartAt())) {
            throw new ServiceException(400, "endAt must be on or after startAt");
        }
        HrTrainingPlan p = new HrTrainingPlan();
        p.setName(req.getName());
        p.setStartAt(req.getStartAt());
        p.setEndAt(req.getEndAt());
        p.setCapacity(req.getCapacity() != null ? req.getCapacity() : 50);
        p.setStatus(PLAN_STATUS_DRAFT);
        p.setTenantId(currentTenantId());
        planMapper.insert(p);
        log.info("Created training plan id={} name={}", p.getId(), p.getName());
        return p;
    }

    @Transactional
    public HrTrainingRecord enroll(Long planId, Long employeeId) {
        requireTenant();
        HrTrainingPlan p = planMapper.selectById(planId);
        if (p == null) throw new ServiceException(404, "Training plan not found: " + planId);
        ensureSameTenant(p);

        // Idempotent: return existing record if already enrolled.
        HrTrainingRecord existing = recordMapper.selectOne(
            new LambdaQueryWrapper<HrTrainingRecord>()
                .eq(HrTrainingRecord::getPlanId, planId)
                .eq(HrTrainingRecord::getEmployeeId, employeeId)
                .last("LIMIT 1"));
        if (existing != null) return existing;

        HrTrainingRecord r = new HrTrainingRecord();
        r.setPlanId(planId);
        r.setEmployeeId(employeeId);
        r.setTenantId(currentTenantId());
        recordMapper.insert(r);
        log.info("Enrolled plan={} employee={}", planId, employeeId);
        return r;
    }

    @Transactional
    public HrTrainingRecord complete(CompleteTrainingRequest req) {
        requireTenant();
        HrTrainingRecord r = recordMapper.selectOne(
            new LambdaQueryWrapper<HrTrainingRecord>()
                .eq(HrTrainingRecord::getPlanId, req.getPlanId())
                .eq(HrTrainingRecord::getEmployeeId, req.getEmployeeId())
                .last("LIMIT 1"));
        if (r == null) {
            throw new ServiceException(404, "Training record not found; enroll first");
        }
        ensureSameTenant(r);
        r.setCompletedAt(LocalDateTime.now());
        if (req.getScore() != null) {
            r.setScore(req.getScore().setScale(2, RoundingMode.HALF_UP));
        }
        recordMapper.updateById(r);
        log.info("Completed training record id={}", r.getId());
        return r;
    }

    public List<HrTrainingRecord> listByEmployee(Long employeeId) {
        requireTenant();
        return recordMapper.findByEmployee(employeeId);
    }

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

    private void ensureSameTenant(HrTrainingPlan p) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || p.getTenantId() == null || !tid.equals(p.getTenantId())) {
            throw new ServiceException(404, "Training plan not found: " + p.getId());
        }
    }

    private void ensureSameTenant(HrTrainingRecord r) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || r.getTenantId() == null || !tid.equals(r.getTenantId())) {
            throw new ServiceException(404, "Training record not found: " + r.getId());
        }
    }
}
