package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrResignation;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrResignationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated HR dashboard counters. Returned as {@code Map<String,Long>} so it
 * round-trips through {@link com.lumen.common.core.domain.R} cleanly without
 * needing a DTO per call site.
 *
 * <p>All counts are tenant-scoped via {@code TenantLineInnerInterceptor} — the
 * explicit {@code eq(...)} clauses here are status filters, not tenant guards.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final HrEmployeeMapper employeeMapper;
    private final HrResignationMapper resignationMapper;

    public Map<String, Long> stats() {
        if (UserContextHolder.get() == null || UserContextHolder.getTenantId() == null) {
            throw new ServiceException(401, "Missing tenant context");
        }

        Long total = employeeMapper.selectCount(null);
        Long active = employeeMapper.selectCount(
            new LambdaQueryWrapper<HrEmployee>().eq(HrEmployee::getStatus, EmployeeService.STATUS_ACTIVE));
        Long probation = employeeMapper.selectCount(
            new LambdaQueryWrapper<HrEmployee>().eq(HrEmployee::getStatus, EmployeeService.STATUS_PROBATION));
        Long resigned = employeeMapper.selectCount(
            new LambdaQueryWrapper<HrEmployee>().eq(HrEmployee::getStatus, EmployeeService.STATUS_RESIGNED));

        // Pending resignation queue (submitted or pending approval).
        Long pendingResignations = resignationMapper.selectCount(
            new LambdaQueryWrapper<HrResignation>()
                .in(HrResignation::getStatus,
                    ResignationService.STATUS_SUBMITTED,
                    ResignationService.STATUS_PENDING_APPROVAL));

        Map<String, Long> out = new HashMap<>();
        out.put("totalEmployees", total == null ? 0L : total);
        out.put("activeEmployees", active == null ? 0L : active);
        out.put("probationEmployees", probation == null ? 0L : probation);
        out.put("resignedEmployees", resigned == null ? 0L : resigned);
        out.put("pendingResignations", pendingResignations == null ? 0L : pendingResignations);
        return out;
    }
}
