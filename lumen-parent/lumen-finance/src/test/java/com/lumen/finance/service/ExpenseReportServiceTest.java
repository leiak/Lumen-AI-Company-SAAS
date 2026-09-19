package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinExpenseReport;
import com.lumen.finance.mapper.FinExpenseReportMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceTest {

    @Mock private FinExpenseReportMapper reportMapper;
    @InjectMocks private ExpenseReportService expenseService;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).build());
    }
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private FinExpenseReport stub(String status, Long applicant) {
        FinExpenseReport r = new FinExpenseReport();
        r.setId(50L);
        r.setTenantId(TID);
        r.setApplicantId(applicant);
        r.setTotalAmount(new BigDecimal("500"));
        r.setStatus(status);
        return r;
    }

    // ---------- get cross-tenant ----------

    @Test
    void get_crossTenant_returns404() {
        FinExpenseReport r = stub("draft", UID);
        r.setTenantId(999L);
        when(reportMapper.selectById(50L)).thenReturn(r);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.get(50L));
        assertEquals(404, ex.getCode());
    }

    // ---------- submit: only draft ----------

    @Test
    void submit_draft_setsSubmittedAndTimestamp() {
        when(reportMapper.selectById(50L)).thenReturn(stub("draft", UID));
        FinExpenseReport result = expenseService.submit(50L);
        assertEquals("submitted", result.getStatus());
        assertNotNull(result.getSubmittedAt());
    }

    @Test
    void submit_alreadySubmitted_throws409() {
        when(reportMapper.selectById(50L)).thenReturn(stub("submitted", UID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.submit(50L));
        assertEquals(409, ex.getCode());
    }

    // ---------- approve: only submitted ----------

    @Test
    void approve_submitted_succeeds() {
        when(reportMapper.selectById(50L)).thenReturn(stub("submitted", UID));
        FinExpenseReport result = expenseService.approve(50L, "ok");
        assertEquals("approved", result.getStatus());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    void approve_alreadyApproved_throws409() {
        when(reportMapper.selectById(50L)).thenReturn(stub("approved", UID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.approve(50L, "x"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void approve_draft_throws409() {
        when(reportMapper.selectById(50L)).thenReturn(stub("draft", UID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.approve(50L, "x"));
        assertEquals(409, ex.getCode());
    }

    // ---------- reject ----------

    @Test
    void reject_submitted_succeeds() {
        when(reportMapper.selectById(50L)).thenReturn(stub("submitted", UID));
        FinExpenseReport result = expenseService.reject(50L, "reason");
        assertEquals("rejected", result.getStatus());
    }

    @Test
    void reject_draft_throws409() {
        when(reportMapper.selectById(50L)).thenReturn(stub("draft", UID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.reject(50L, "x"));
        assertEquals(409, ex.getCode());
    }

    // ---------- markPaid ----------

    @Test
    void markPaid_approved_succeeds() {
        when(reportMapper.selectById(50L)).thenReturn(stub("approved", UID));
        FinExpenseReport result = expenseService.markPaid(50L);
        assertEquals("paid", result.getStatus());
    }

    @Test
    void markPaid_submitted_throws409() {
        when(reportMapper.selectById(50L)).thenReturn(stub("submitted", UID));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> expenseService.markPaid(50L));
        assertEquals(409, ex.getCode());
    }

    // ---------- saveDraft ----------

    @Test
    void saveDraft_persistsWithCurrentUser() {
        when(reportMapper.insert(any(FinExpenseReport.class))).thenAnswer(inv -> {
            FinExpenseReport r = inv.getArgument(0);
            r.setId(77L);
            return 1;
        });
        Map<String, Object> item = new HashMap<>();
        item.put("subjectId", 11);
        item.put("amount", 100);
        FinExpenseReport result = expenseService.saveDraft(
            new com.lumen.finance.dto.ExpenseReportRequest() {{
                setDepartmentId(10L);
                setTotalAmount(new BigDecimal("100"));
                setItems(List.of(item));
            }});
        assertNotNull(result.getId());
        assertEquals(UID, result.getApplicantId());
        assertEquals("draft", result.getStatus());
    }
}
