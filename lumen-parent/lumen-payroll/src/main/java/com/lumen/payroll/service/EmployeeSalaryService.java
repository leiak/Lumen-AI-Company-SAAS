package com.lumen.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.SaveEmployeeSalaryRequest;
import com.lumen.payroll.entity.PayEmployeeSalary;
import com.lumen.payroll.mapper.PayEmployeeSalaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工薪资档案服务。
 * 安全要求 #6: effectiveTo (如有) > effectiveFrom。
 * 安全要求 #7: UNIQUE(employee_id, effective_from, deleted)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSalaryService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_SUSPENDED = "suspended";

    private final PayEmployeeSalaryMapper mapper;

    public PayEmployeeSalary get(Long id) {
        UserContext ctx = requireUserContext();
        PayEmployeeSalary e = mapper.selectById(id);
        if (e == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(e.getTenantId()))) {
            throw new ServiceException(404, "Employee salary not found: " + id);
        }
        return e;
    }

    public List<PayEmployeeSalary> listByEmployee(Long employeeId) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return mapper.findByEmployee(employeeId).stream()
            .filter(e -> ctx.getTenantId().equals(e.getTenantId()))
            .toList();
    }

    public List<PayEmployeeSalary> listAll() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return mapper.selectList(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getTenantId, ctx.getTenantId())
            .eq(PayEmployeeSalary::getDeleted, 0)
            .orderByDesc(PayEmployeeSalary::getId));
    }

    /**
     * 安全要求 #6: effective_to > effective_from。
     * 安全要求 #7: UNIQUE(employee_id, effective_from, deleted)。
     */
    @Transactional
    public PayEmployeeSalary save(SaveEmployeeSalaryRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        if (req.getEffectiveTo() != null && !req.getEffectiveTo().isAfter(req.getEffectiveFrom())) {
            throw new ServiceException(400,
                "effectiveTo must be strictly after effectiveFrom: "
                    + req.getEffectiveFrom() + " -> " + req.getEffectiveTo());
        }
        // 检查 UNIQUE
        long conflict = mapper.selectCount(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getEmployeeId, req.getEmployeeId())
            .eq(PayEmployeeSalary::getEffectiveFrom, req.getEffectiveFrom())
            .eq(PayEmployeeSalary::getDeleted, 0));
        if (conflict > 0) {
            throw new ServiceException(409,
                "Employee salary already exists for employeeId=" + req.getEmployeeId()
                    + " effectiveFrom=" + req.getEffectiveFrom());
        }
        PayEmployeeSalary e = new PayEmployeeSalary();
        e.setTenantId(ctx.getTenantId());
        e.setEmployeeId(req.getEmployeeId());
        e.setStructureId(req.getStructureId());
        e.setBaseSalary(req.getBaseSalary());
        e.setEffectiveFrom(req.getEffectiveFrom());
        e.setEffectiveTo(req.getEffectiveTo());
        e.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        try {
            mapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Duplicate employee salary for same effectiveFrom", ex);
        }
        log.info("Employee salary saved id={} employeeId={} effectiveFrom={}",
            e.getId(), e.getEmployeeId(), e.getEffectiveFrom());
        return e;
    }

    @Transactional
    public void delete(Long id) {
        PayEmployeeSalary e = get(id);
        mapper.deleteById(e.getId());
        log.info("Employee salary deleted id={}", id);
    }

    /**
     * 内部用: 找生效日期当日生效的 active 薪资 (算薪循环用).
     * 缺失 → 抛 404.
     */
    public PayEmployeeSalary findEffective(Long employeeId, LocalDate date, Long tenantId) {
        PayEmployeeSalary e = mapper.findByEmployeeAndEffectiveAt(employeeId, date);
        if (e == null || (tenantId != null && !tenantId.equals(e.getTenantId()))) {
            throw new ServiceException(404,
                "No active employee salary for employeeId=" + employeeId + " on " + date);
        }
        return e;
    }

    /**
     * 内部用: 列出所有 tenant 下的 active 薪资 (算薪循环用)。
     * 不用 ctx 校验, 但 tenantId 必须传。
     */
    public List<PayEmployeeSalary> listActiveByTenant(Long tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getTenantId, tenantId)
            .eq(PayEmployeeSalary::getStatus, STATUS_ACTIVE)
            .eq(PayEmployeeSalary::getDeleted, 0));
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}