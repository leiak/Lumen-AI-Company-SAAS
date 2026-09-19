package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.SubmitResignationRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrResignation;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrResignationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Resignation submission. Effective resignations cannot be cancelled (per requirement).
 *
 * <p>Asset return + permission revocation is intentionally TODO — those require cross-
 * service calls to assets-service and auth-service that aren't wired in P4.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResignationService {

    public static final int STATUS_SUBMITTED = 0;
    public static final int STATUS_PENDING_APPROVAL = 1;
    public static final int STATUS_APPROVED = 2;
    public static final int STATUS_EFFECTIVE = 3;
    public static final int STATUS_REJECTED = 4;
    public static final int STATUS_REVOKED = 5;

    private final HrResignationMapper resignationMapper;
    private final HrEmployeeMapper employeeMapper;

    @Transactional
    public HrResignation submit(SubmitResignationRequest req) {
        requireTenant();
        HrEmployee e = employeeMapper.selectById(req.getEmployeeId());
        if (e == null) throw new ServiceException(404, "Employee not found: " + req.getEmployeeId());
        ensureSameTenant(e);

        // Employee must already be active or on probation.
        Integer s = e.getStatus();
        if (s == null || s == EmployeeService.STATUS_RESIGNED) {
            throw new ServiceException(409, "Employee is not eligible for resignation");
        }

        HrResignation r = new HrResignation();
        r.setEmployeeId(req.getEmployeeId());
        r.setReason(req.getReason());
        r.setSubmitAt(LocalDateTime.now());
        r.setEffectiveAt(req.getEffectiveAt());
        r.setStatus(STATUS_SUBMITTED);
        r.setTenantId(currentTenantId());
        resignationMapper.insert(r);

        // TODO: trigger workflow approval (lumen-workflow) once the integration is wired.
        // TODO: schedule asset-return checklist + permission revocation (assets-service / auth-service).

        log.info("Submitted resignation id={} employeeId={}", r.getId(), req.getEmployeeId());
        return r;
    }

    /**
     * Cancel a resignation. Refuses to touch anything that's already effective —
     * those need a separate HR remediation flow.
     */
    @Transactional
    public HrResignation revoke(Long id) {
        requireTenant();
        HrResignation r = resignationMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Resignation not found: " + id);
        ensureSameTenant(r);

        if (r.getStatus() == null || r.getStatus() >= STATUS_EFFECTIVE) {
            throw new ServiceException(409,
                "Cannot revoke a resignation that's already effective; remediation only");
        }
        r.setStatus(STATUS_REVOKED);
        resignationMapper.updateById(r);
        log.info("Revoked resignation id={}", id);
        return r;
    }

    public HrResignation getById(Long id) {
        requireTenant();
        HrResignation r = resignationMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Resignation not found: " + id);
        ensureSameTenant(r);
        return r;
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

    private void ensureSameTenant(HrEmployee e) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || e.getTenantId() == null || !tid.equals(e.getTenantId())) {
            throw new ServiceException(404, "Employee not found: " + e.getId());
        }
    }

    private void ensureSameTenant(HrResignation r) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || r.getTenantId() == null || !tid.equals(r.getTenantId())) {
            throw new ServiceException(404, "Resignation not found: " + r.getId());
        }
    }
}
