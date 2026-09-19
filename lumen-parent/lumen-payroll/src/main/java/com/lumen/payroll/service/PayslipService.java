package com.lumen.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.CalculatePayrollRequest;
import com.lumen.payroll.dto.ConfirmPayslipRequest;
import com.lumen.payroll.entity.PayEmployeeSalary;
import com.lumen.payroll.entity.PaySalaryStructure;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.entity.PaySlipItem;
import com.lumen.payroll.mapper.PaySlipItemMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工资条核心服务。
 *
 * <p>状态机: {@code draft -> calculated -> confirmed -> paid}。
 * 安全要求 #8: markPaid 必须先 confirm;
 * 安全要求 #9: confirm 必须先 calculate;
 * 安全要求 #10: calculate 期间不能重复跑 (已有 non-draft 时拒绝重算)。</p>
 *
 * <p>算薪公式 (calculate()):</p>
 * <pre>
 *   gross = baseSalary + sum(allowance components)
 *   socialEmployee = socialSecurityService.employeeAmount(empId, period)
 *   taxableIncome (本月) = gross - 5000 (起征) - socialEmployee - specialDeduction
 *   taxAmount = taxService.calculateTax(taxableIncome, cumulativeIncome, specialDeductionJson)
 *   totalDeduction = socialEmployee + taxAmount
 *   netSalary = gross - totalDeduction
 * </pre>
 *
 * <p>算薪循环遍历所有 active employee_salary (按 effectiveFrom &lt;= periodEnd),
 * 按 structure.components (baseSalary + allowance) 生成 gross。
 * TODO: employee_id 来源 — 目前依赖本地 employee stub 表 (P5 调 lumen-hr Feign client).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_CALCULATED = "calculated";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_PAID = "paid";

    private final PaySlipMapper slipMapper;
    private final PaySlipItemMapper itemMapper;
    private final EmployeeSalaryService employeeSalaryService;
    private final SalaryStructureService structureService;
    private final SocialSecurityService socialSecurityService;
    private final TaxService taxService;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public PaySlip get(Long id) {
        UserContext ctx = requireUserContext();
        PaySlip s = slipMapper.selectById(id);
        if (s == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(s.getTenantId()))) {
            throw new ServiceException(404, "Payslip not found: " + id);
        }
        return s;
    }

    /**
     * 安全要求 #14: 工资条隐私保护 — admin 看全量, 员工只能看自己。
     * caller 通过 controller 传 employeeHint, 此处不二次校验。
     */
    public List<PaySlip> listByPeriod(String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return slipMapper.findByPeriod(period).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
    }

    public List<PaySlip> listByEmployee(Long employeeId, String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        List<PaySlip> all = period == null
            ? slipMapper.findByEmployee(employeeId)
            : slipMapper.findByEmployeeAndPeriodList(employeeId, period);
        return all.stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
    }

    /**
     * 安全要求 #3: 员工查自己的 (/pay/slip/my), employeeId 从 ctx 取, 不接受 query。
     */
    public List<PaySlip> mySlips() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        if (ctx.getUserId() == null) {
            throw new ServiceException(401, "No user in context");
        }
        // TODO P5: ctx.userId → employeeId (sys_user.user_id ↔ hr_employee.user_id)
        // 当前用 ctx.userId 直接当 employeeId (本地 stub 表, 见 SocialSecurityService 等)。
        Long employeeId = ctx.getUserId();
        return slipMapper.findByEmployee(employeeId).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
    }

    public List<PaySlipItem> items(Long slipId) {
        get(slipId); // tenant check
        return itemMapper.findBySlip(slipId);
    }

    // ---------------------------------------------------------------
    // calculate
    // ---------------------------------------------------------------

    /**
     * 安全要求 #10: 已有 non-draft slip 拒绝重算.
     * 遍历所有 active employee_salary (按 periodEnd 当日生效), 计算每位员工本月 slip + items。
     */
    @Transactional
    public int calculate(CalculatePayrollRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        String period = req.getPeriod();
        LocalDate periodEnd = parsePeriodEnd(period);

        // 检查是否已有 non-draft
        List<PaySlip> existing = slipMapper.findByPeriod(period).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
        boolean hasNonDraft = existing.stream()
            .anyMatch(s -> !STATUS_DRAFT.equals(s.getStatus()));
        if (hasNonDraft) {
            throw new ServiceException(409,
                "Period " + period + " has already been calculated; cannot re-run");
        }
        // 已有 draft 允许清理 (因为草稿可重算)
        for (PaySlip s : existing) {
            itemMapper.delete(
                new LambdaQueryWrapper<PaySlipItem>().eq(PaySlipItem::getSlipId, s.getId()));
            slipMapper.deleteById(s.getId());
        }

        // 列出 tenant 下所有 active 员工薪资
        List<PayEmployeeSalary> salaries = employeeSalaryService.listActiveByTenant(ctx.getTenantId());
        int created = 0;
        for (PayEmployeeSalary es : salaries) {
            // effectiveTo null 或 > periodEnd 当日
            if (es.getEffectiveTo() != null && es.getEffectiveTo().isBefore(periodEnd)) {
                continue;
            }
            if (es.getEffectiveFrom() == null || es.getEffectiveFrom().isAfter(periodEnd)) {
                continue;
            }
            PaySalaryStructure struct = structureService.getInternal(es.getStructureId(), ctx.getTenantId());
            BigDecimal gross = computeGross(es, struct);
            BigDecimal socialEmp = socialSecurityService.employeeAmount(es.getEmployeeId(), period);

            // taxableIncome (本月) = gross - 5000 - socialEmp (简化, 专项附加 0)
            BigDecimal basicDeduction = TaxService.BASIC_DEDUCTION;
            BigDecimal taxableIncome = gross.subtract(basicDeduction).subtract(socialEmp);
            if (taxableIncome.signum() < 0) taxableIncome = BigDecimal.ZERO;

            BigDecimal cumulativeIncome = taxService.cumulativeIncome(es.getEmployeeId(), period)
                .add(gross);
            BigDecimal taxAmount = taxService.calculateTax(taxableIncome, cumulativeIncome, null);
            if (taxAmount.signum() < 0) taxAmount = BigDecimal.ZERO;

            BigDecimal totalDeduction = socialEmp.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netSalary = gross.subtract(totalDeduction).setScale(2, RoundingMode.HALF_UP);

            PaySlip slip = new PaySlip();
            slip.setTenantId(ctx.getTenantId());
            slip.setPeriod(period);
            slip.setEmployeeId(es.getEmployeeId());
            slip.setGrossSalary(gross);
            slip.setTotalDeduction(totalDeduction);
            slip.setNetSalary(netSalary);
            slip.setStatus(STATUS_CALCULATED);
            slip.setCalculatedAt(LocalDateTime.now());
            slipMapper.insert(slip);
            created++;

            // 写入 pay_slip_item (earning / deduction)
            List<PaySlipItem> items = new ArrayList<>();
            // earnings: baseSalary + components.allowance 项
            Map<String, Object> comps = struct.getComponents();
            if (comps != null) {
                for (Map.Entry<String, Object> e : comps.entrySet()) {
                    String k = e.getKey();
                    Object v = e.getValue();
                    if (!(v instanceof Number n)) continue;
                    BigDecimal amount = BigDecimal.valueOf(n.doubleValue());
                    if (amount.signum() <= 0) continue;
                    PaySlipItem it = new PaySlipItem();
                    it.setTenantId(ctx.getTenantId());
                    it.setSlipId(slip.getId());
                    it.setItemType("earning");
                    it.setItemCode(k);
                    it.setItemName(k);
                    it.setAmount(amount);
                    it.setFormula("baseSalary * structure");
                    items.add(it);
                }
            }
            // deduction: socialEmployee
            if (socialEmp.signum() > 0) {
                PaySlipItem ds = new PaySlipItem();
                ds.setTenantId(ctx.getTenantId());
                ds.setSlipId(slip.getId());
                ds.setItemType("deduction");
                ds.setItemCode("socialEmployee");
                ds.setItemName("socialEmployee");
                ds.setAmount(socialEmp);
                ds.setFormula("sum(pension+medical+unemployment+housingFund) * employeeRate");
                items.add(ds);
            }
            // deduction: tax
            if (taxAmount.signum() > 0) {
                PaySlipItem dt = new PaySlipItem();
                dt.setTenantId(ctx.getTenantId());
                dt.setSlipId(slip.getId());
                dt.setItemType("deduction");
                dt.setItemCode("tax");
                dt.setItemName("individualIncomeTax");
                dt.setAmount(taxAmount);
                dt.setFormula("taxService.calculateTax");
                items.add(dt);
            }
            for (PaySlipItem it : items) {
                itemMapper.insert(it);
            }

            // 写 pay_tax 记录
            taxService.saveRecord(
                slip.getId(),
                es.getEmployeeId(),
                period,
                taxableIncome,
                taxAmount,
                BigDecimal.ZERO, // taxRate 由具体实现计算
                cumulativeIncome,
                taxAmount,
                null
            );
        }
        log.info("Payroll calculated period={} createdSlips={} tenant={}", period, created, ctx.getTenantId());
        return created;
    }

    /**
     * gross = baseSalary + 所有非 baseSalary 的 component 数值之和。
     * baseSalary 字段优先, components 里的 baseSalary 是次选 (兼容旧结构)。
     */
    private BigDecimal computeGross(PayEmployeeSalary es, PaySalaryStructure struct) {
        BigDecimal total = es.getBaseSalary() == null ? BigDecimal.ZERO : es.getBaseSalary();
        Map<String, Object> comps = struct.getComponents();
        if (comps != null) {
            for (Map.Entry<String, Object> e : comps.entrySet()) {
                if ("baseSalary".equals(e.getKey())) continue; // 已包含
                Object v = e.getValue();
                if (v instanceof Number n) {
                    total = total.add(BigDecimal.valueOf(n.doubleValue()));
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parsePeriodEnd(String period) {
        int y = Integer.parseInt(period.substring(0, 4));
        int m = Integer.parseInt(period.substring(5, 7));
        // periodEnd = 当月最后一天
        return LocalDate.of(y, m, 1).withDayOfMonth(
            LocalDate.of(y, m, 1).lengthOfMonth());
    }

    // ---------------------------------------------------------------
    // confirm
    // ---------------------------------------------------------------

    /**
     * 安全要求 #9: confirm 必须先 calculate (status=calculated)。
     * 安全要求 #8: 批量确认, slip 必须存在且 status=calculated。
     */
    @Transactional
    public List<PaySlip> confirm(ConfirmPayslipRequest req) {
        UserContext ctx = requireUserContext();
        List<PaySlip> updated = new ArrayList<>();
        for (Long id : req.getSlipIds()) {
            PaySlip s = get(id);
            if (!STATUS_CALCULATED.equals(s.getStatus())) {
                throw new ServiceException(409,
                    "Slip " + id + " is not in calculated state; status=" + s.getStatus());
            }
            // 校验 item 已完整 (>= earning 1 + deduction 0/1/2)
            List<PaySlipItem> items = itemMapper.findBySlip(s.getId());
            if (items.isEmpty()) {
                throw new ServiceException(409,
                    "Slip " + id + " has no items; cannot confirm");
            }
            s.setStatus(STATUS_CONFIRMED);
            s.setConfirmedAt(LocalDateTime.now());
            slipMapper.updateById(s);
            updated.add(s);
        }
        log.info("Payslips confirmed count={} by={}", updated.size(), ctx.getUserId());
        return updated;
    }

    // ---------------------------------------------------------------
    // markPaid
    // ---------------------------------------------------------------

    /**
     * 安全要求 #8: markPaid 必须先 confirm (status=confirmed)。
     */
    @Transactional
    public PaySlip markPaid(Long id) {
        PaySlip s = get(id);
        if (!STATUS_CONFIRMED.equals(s.getStatus())) {
            throw new ServiceException(409,
                "Slip " + id + " must be confirmed before paid; status=" + s.getStatus());
        }
        s.setStatus(STATUS_PAID);
        s.setPaidAt(LocalDateTime.now());
        slipMapper.updateById(s);
        log.info("Payslip marked paid id={}", id);
        return s;
    }

    /**
     * 批量 markPaid (银行报盘确认后批量入账)。
     */
    @Transactional
    public List<PaySlip> markPaidBatch(List<Long> ids) {
        List<PaySlip> out = new ArrayList<>();
        for (Long id : ids) {
            out.add(markPaid(id));
        }
        return out;
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}