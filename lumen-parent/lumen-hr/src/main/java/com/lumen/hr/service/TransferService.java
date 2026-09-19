package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.TransferRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrTransfer;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Transfer approval facade. Delegates the actual move to {@link EmployeeService#transfer}
 * (which writes the audit row and fires {@link EmployeeTransferredEvent}) — this service
 * only enforces the workflow gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final HrTransferMapper transferMapper;
    private final HrEmployeeMapper employeeMapper;
    private final EmployeeService employeeService;

    /**
     * Approve-and-apply: delegate to {@link EmployeeService#transfer} which writes
     * the {@link HrTransfer} row + updates the employee in one transaction.
     */
    public HrEmployee approve(TransferRequest req) {
        if (UserContextHolder.get() == null) {
            throw new ServiceException(401, "No user context");
        }
        return employeeService.transfer(
            req.getEmployeeId(), req.getToDeptId(), req.getToPostId(), req.getEffectiveAt());
    }

    public List<HrTransfer> history(Long employeeId) {
        if (UserContextHolder.get() == null || UserContextHolder.getTenantId() == null) {
            throw new ServiceException(401, "Missing tenant context");
        }
        if (employeeId == null) throw new ServiceException(400, "employeeId is required");
        HrEmployee e = employeeMapper.selectById(employeeId);
        if (e == null) throw new ServiceException(404, "Employee not found: " + employeeId);
        // Cross-tenant disclosure is hidden as 404.
        Long tid = UserContextHolder.getTenantId();
        if (!tid.equals(e.getTenantId())) {
            throw new ServiceException(404, "Employee not found: " + employeeId);
        }
        return transferMapper.findByEmployee(employeeId);
    }
}
