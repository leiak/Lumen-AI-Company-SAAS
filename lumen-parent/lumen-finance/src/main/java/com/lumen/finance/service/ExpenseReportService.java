package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.dto.ExpenseReportRequest;
import com.lumen.finance.entity.FinExpenseReport;
import com.lumen.finance.mapper.FinExpenseReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报销 service。
 *
 * <p>submit 触发 workflow start —— 当前是 TODO, 留接口给 lumen-workflow;
 * approve 由 workflow 回调; markPaid 由 PaymentService 完成付款后调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseReportService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_PAID = "paid";

    private final FinExpenseReportMapper reportMapper;

    public FinExpenseReport get(Long id) {
        UserContext ctx = requireUserContext();
        FinExpenseReport r = reportMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Expense report not found: " + id);
        // 安全要求 #2: 自己的 or finance_admin
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(r.getTenantId())) {
            throw new ServiceException(404, "Expense report not found: " + id);
        }
        return r;
    }

    /**
     * 仅返回当前用户的报销单 (controller 层会强校验 isAuthenticated())。
     */
    public List<FinExpenseReport> myList() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return reportMapper.listByApplicant(ctx.getUserId());
    }

    @Transactional
    public FinExpenseReport saveDraft(ExpenseReportRequest req) {
        UserContext ctx = requireUserContext();
        FinExpenseReport r = new FinExpenseReport();
        r.setTenantId(ctx.getTenantId());
        r.setApplicantId(ctx.getUserId());
        r.setDepartmentId(req.getDepartmentId());
        r.setTotalAmount(req.getTotalAmount());
        r.setItems(req.getItems());
        r.setStatus(STATUS_DRAFT);
        reportMapper.insert(r);
        return r;
    }

    /**
     * 提交触发工作流。TODO: 实际调用 lumen-workflow EngineService.startInstance。
     */
    @Transactional
    public FinExpenseReport submit(Long id) {
        FinExpenseReport r = get(id);
        if (!STATUS_DRAFT.equals(r.getStatus())) {
            throw new ServiceException(409, "Only draft can be submitted; status=" + r.getStatus());
        }
        // TODO P5: workflowEngine.startInstance("expense", "EXP-" + id, Map.of("expenseId", id));
        r.setStatus(STATUS_SUBMITTED);
        r.setSubmittedAt(LocalDateTime.now());
        // r.setWorkflowInstanceId(startedInstanceId);
        reportMapper.updateById(r);
        log.info("Expense report submitted id={} (workflow TODO)", r.getId());
        return r;
    }

    /**
     * 由工作流回调 (或 finance_admin 直接调用) 把单据置为 approved。
     */
    @Transactional
    public FinExpenseReport approve(Long id, String comment) {
        FinExpenseReport r = get(id);
        if (!STATUS_SUBMITTED.equals(r.getStatus())) {
            throw new ServiceException(409, "Only submitted can be approved; status=" + r.getStatus());
        }
        r.setStatus(STATUS_APPROVED);
        r.setApprovedAt(LocalDateTime.now());
        reportMapper.updateById(r);
        log.info("Expense report approved id={} comment={}", r.getId(), comment);
        return r;
    }

    @Transactional
    public FinExpenseReport reject(Long id, String comment) {
        FinExpenseReport r = get(id);
        if (!STATUS_SUBMITTED.equals(r.getStatus())) {
            throw new ServiceException(409, "Only submitted can be rejected; status=" + r.getStatus());
        }
        r.setStatus(STATUS_REJECTED);
        r.setApprovedAt(LocalDateTime.now());
        reportMapper.updateById(r);
        log.info("Expense report rejected id={} comment={}", r.getId(), comment);
        return r;
    }

    /**
     * 付款完成时由 PaymentService 调用: 把 approved 单据置为 paid。
     */
    @Transactional
    public FinExpenseReport markPaid(Long id) {
        FinExpenseReport r = get(id);
        if (!STATUS_APPROVED.equals(r.getStatus())) {
            throw new ServiceException(409, "Only approved can be paid; status=" + r.getStatus());
        }
        r.setStatus(STATUS_PAID);
        reportMapper.updateById(r);
        log.info("Expense report paid id={}", r.getId());
        return r;
    }

    public List<FinExpenseReport> findByApplicantAndStatus(Long applicantId, String status) {
        requireTenant();
        return reportMapper.findByApplicantAndStatus(applicantId, status);
    }

    // ---------- helpers ----------
    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private UserContext requireTenant() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }
}
