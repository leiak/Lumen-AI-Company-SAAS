package com.lumen.payroll.controller;

import com.lumen.common.core.domain.R;
import com.lumen.payroll.dto.CalculatePayrollRequest;
import com.lumen.payroll.dto.ConfirmPayslipRequest;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.entity.PaySlipItem;
import com.lumen.payroll.service.PayslipReadService;
import com.lumen.payroll.service.PayslipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService payslipService;
    private final PayslipReadService payslipReadService;

    @PostMapping("/calc/run")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Integer> calculate(@RequestBody @Valid CalculatePayrollRequest req) {
        return R.ok(payslipService.calculate(req));
    }

    @GetMapping("/slip/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySlip>> listByPeriod(@RequestParam String period) {
        return R.ok(payslipService.listByPeriod(period));
    }

    /**
     * 安全要求 #3: 员工自己 slip, employeeId 从 ctx 取, 不接受 query 参数。
     */
    @GetMapping("/slip/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<PaySlip>> mySlips() {
        return R.ok(payslipService.mySlips());
    }

    @GetMapping("/slip/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySlip>> byEmployee(@PathVariable Long employeeId,
                                       @RequestParam(required = false) String period) {
        return R.ok(payslipService.listByEmployee(employeeId, period));
    }

    /**
     * 安全要求 #14: 工资条隐私 — admin 看全量, 员工只能看自己 (此处仅 admin)。
     */
    @GetMapping("/slip/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PaySlip> get(@PathVariable Long id) {
        return R.ok(payslipService.get(id));
    }

    @GetMapping("/slip/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin','user')")
    public R<List<PaySlipItem>> items(@PathVariable Long id) {
        return R.ok(payslipService.items(id));
    }

    @PostMapping("/slip/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<List<PaySlip>> confirm(@RequestBody @Valid ConfirmPayslipRequest req) {
        return R.ok(payslipService.confirm(req));
    }

    @PostMapping("/slip/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<PaySlip> markPaid(@PathVariable Long id) {
        return R.ok(payslipService.markPaid(id));
    }

    /**
     * 安全要求 #4: mark-read 必须校验 employee == ctx.userId (service 层强制)。
     */
    @PostMapping("/slip/{id}/mark-read")
    @PreAuthorize("isAuthenticated()")
    public R<com.lumen.payroll.entity.PayPayslipRead> markRead(@PathVariable Long id) {
        return R.ok(payslipReadService.markRead(id));
    }

    @GetMapping("/slip/{id}/read-count")
    @PreAuthorize("hasAnyRole('super_admin','admin','payroll_admin')")
    public R<Long> readCount(@PathVariable Long id) {
        return R.ok(payslipReadService.readCount(id));
    }

    @GetMapping("/slip/unread")
    @PreAuthorize("isAuthenticated()")
    public R<List<PaySlip>> unread() {
        return R.ok(payslipReadService.unreadSlips());
    }
}