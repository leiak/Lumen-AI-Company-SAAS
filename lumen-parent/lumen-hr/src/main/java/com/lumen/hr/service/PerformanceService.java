package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.StartCycleRequest;
import com.lumen.hr.dto.SubmitPerformanceRequest;
import com.lumen.hr.entity.HrPerformanceCycle;
import com.lumen.hr.entity.HrPerformanceScore;
import com.lumen.hr.mapper.HrPerformanceCycleMapper;
import com.lumen.hr.mapper.HrPerformanceScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Performance cycle + scoring.
 *
 * <p>Cycle status: 0=未开始 1=进行中 2=已结束</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    public static final int CYCLE_STATUS_PENDING = 0;
    public static final int CYCLE_STATUS_RUNNING = 1;
    public static final int CYCLE_STATUS_CLOSED = 2;

    private final HrPerformanceCycleMapper cycleMapper;
    private final HrPerformanceScoreMapper scoreMapper;

    @Transactional
    public HrPerformanceCycle startCycle(StartCycleRequest req) {
        requireTenant();
        if (req.getPeriodEnd().isBefore(req.getPeriodStart())) {
            throw new ServiceException(400, "periodEnd must be on or after periodStart");
        }
        HrPerformanceCycle c = new HrPerformanceCycle();
        c.setName(req.getName());
        c.setPeriodStart(req.getPeriodStart());
        c.setPeriodEnd(req.getPeriodEnd());
        c.setStatus(CYCLE_STATUS_RUNNING);
        c.setTenantId(currentTenantId());
        cycleMapper.insert(c);
        log.info("Started performance cycle id={} name={}", c.getId(), c.getName());
        return c;
    }

    @Transactional
    public HrPerformanceScore submitScore(SubmitPerformanceRequest req) {
        requireTenant();
        HrPerformanceCycle c = cycleMapper.selectById(req.getCycleId());
        if (c == null) throw new ServiceException(404, "Performance cycle not found: " + req.getCycleId());
        ensureSameTenant(c);

        if (c.getStatus() == null || c.getStatus() == CYCLE_STATUS_CLOSED) {
            throw new ServiceException(409, "Performance cycle is not accepting scores");
        }
        if (req.getScore() == null
            || req.getScore().compareTo(BigDecimal.ZERO) < 0
            || req.getScore().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ServiceException(400, "score must be between 0 and 100 inclusive");
        }

        HrPerformanceScore s = new HrPerformanceScore();
        s.setCycleId(req.getCycleId());
        s.setEmployeeId(req.getEmployeeId());
        s.setScore(req.getScore().setScale(2, RoundingMode.HALF_UP));
        s.setComment(req.getComment());
        s.setSubmittedAt(LocalDateTime.now());
        s.setTenantId(currentTenantId());
        scoreMapper.insert(s);
        log.info("Submitted performance score id={} cycle={} employee={} score={}",
            s.getId(), req.getCycleId(), req.getEmployeeId(), s.getScore());
        return s;
    }

    public List<HrPerformanceScore> listByCycleAndEmployee(Long cycleId, Long employeeId) {
        requireTenant();
        return scoreMapper.findByCycleAndEmployee(cycleId, employeeId);
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

    private void ensureSameTenant(HrPerformanceCycle c) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || c.getTenantId() == null || !tid.equals(c.getTenantId())) {
            throw new ServiceException(404, "Performance cycle not found: " + c.getId());
        }
    }
}
