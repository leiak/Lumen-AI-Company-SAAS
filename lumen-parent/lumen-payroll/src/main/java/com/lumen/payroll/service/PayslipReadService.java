package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.entity.PayPayslipRead;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.mapper.PayPayslipReadMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工资条已读服务。
 *
 * <p>安全要求 #4: markRead 必须校验 slip.employee_id == ctx.userId, 否则 403。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipReadService {

    private final PayPayslipReadMapper readMapper;
    private final PaySlipMapper slipMapper;

    /**
     * 标记已读。校验 slip 属于当前 employee。
     */
    @Transactional
    public PayPayslipRead markRead(Long slipId) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        if (ctx.getUserId() == null) {
            throw new ServiceException(401, "No user in context");
        }
        PaySlip slip = slipMapper.selectById(slipId);
        if (slip == null || !ctx.getTenantId().equals(slip.getTenantId())) {
            throw new ServiceException(404, "Payslip not found: " + slipId);
        }
        // 安全要求 #4: employee 校验
        if (!ctx.getUserId().equals(slip.getEmployeeId())) {
            throw new ServiceException(403, "Payslip does not belong to current user");
        }
        // 幂等
        PayPayslipRead existing = readMapper.findBySlipAndEmployee(slipId, ctx.getUserId());
        if (existing != null) {
            return existing;
        }
        PayPayslipRead r = new PayPayslipRead();
        r.setTenantId(ctx.getTenantId());
        r.setSlipId(slipId);
        r.setEmployeeId(ctx.getUserId());
        r.setReadAt(LocalDateTime.now());
        readMapper.insert(r);
        log.info("Payslip read marked slipId={} employeeId={}", slipId, ctx.getUserId());
        return r;
    }

    /**
     * 某 slip 的已读人数。
     */
    public long readCount(Long slipId) {
        UserContext ctx = requireUserContext();
        PaySlip slip = slipMapper.selectById(slipId);
        if (slip == null || !ctx.getTenantId().equals(slip.getTenantId())) {
            throw new ServiceException(404, "Payslip not found: " + slipId);
        }
        return readMapper.countBySlip(slipId);
    }

    /**
     * 列出某 slip 的已读明细。
     */
    public List<PayPayslipRead> listBySlip(Long slipId) {
        UserContext ctx = requireUserContext();
        PaySlip slip = slipMapper.selectById(slipId);
        if (slip == null || !ctx.getTenantId().equals(slip.getTenantId())) {
            throw new ServiceException(404, "Payslip not found: " + slipId);
        }
        return readMapper.findBySlip(slipId);
    }

    /**
     * 员工查自己未读 slip (已 confirmed/paid 但未 markRead 的)。
     * TODO: 简化版: 返回 confirmed 状态且当前员工未 markRead 的 slip。
     */
    public List<PaySlip> unreadSlips() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        Long employeeId = ctx.getUserId();
        List<PaySlip> all = slipMapper.findByEmployee(employeeId).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .filter(s -> PayslipService.STATUS_CONFIRMED.equals(s.getStatus())
                || PayslipService.STATUS_PAID.equals(s.getStatus()))
            .toList();
        return all.stream()
            .filter(s -> readMapper.findBySlipAndEmployee(s.getId(), employeeId) == null)
            .toList();
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}