package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.entity.PayPayslipRead;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.mapper.PayPayslipReadMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayslipReadServiceTest {

    @Mock private PayPayslipReadMapper readMapper;
    @Mock private PaySlipMapper slipMapper;

    @InjectMocks private PayslipReadService service;

    private static final long UID = 100L;
    private static final long OTHER = 200L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private PaySlip stubSlip(long id, long employeeId, String status) {
        PaySlip s = new PaySlip();
        s.setId(id);
        s.setTenantId(TID);
        s.setEmployeeId(employeeId);
        s.setStatus(status);
        return s;
    }

    // ---------- markRead: 工资条隐私保护 ----------

    @Test
    void markRead_ownSlip_succeeds() {
        PaySlip s = stubSlip(50L, UID, "confirmed");
        when(slipMapper.selectById(50L)).thenReturn(s);
        when(readMapper.findBySlipAndEmployee(50L, UID)).thenReturn(null);
        when(readMapper.insert(any(PayPayslipRead.class))).thenAnswer(inv -> {
            PayPayslipRead r = inv.getArgument(0);
            r.setId(99L);
            return 1;
        });
        PayPayslipRead out = service.markRead(50L);
        assertEquals(99L, out.getId());
        assertEquals(UID, out.getEmployeeId());
        assertEquals(50L, out.getSlipId());
    }

    @Test
    void markRead_otherEmployeeSlip_throws403() {
        PaySlip s = stubSlip(50L, OTHER, "confirmed"); // slip 属于 200, 当前 ctx 是 100
        when(slipMapper.selectById(50L)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markRead(50L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("does not belong"));
        verify(readMapper, never()).insert(any());
    }

    @Test
    void markRead_slipNotFound_throws404() {
        when(slipMapper.selectById(50L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markRead(50L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void markRead_crossTenant_throws404() {
        PaySlip other = stubSlip(50L, UID, "confirmed");
        other.setTenantId(999L);
        when(slipMapper.selectById(50L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markRead(50L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void markRead_idempotent_returnsExisting() {
        PaySlip s = stubSlip(50L, UID, "confirmed");
        when(slipMapper.selectById(50L)).thenReturn(s);
        PayPayslipRead existing = new PayPayslipRead();
        existing.setId(77L);
        when(readMapper.findBySlipAndEmployee(50L, UID)).thenReturn(existing);
        PayPayslipRead out = service.markRead(50L);
        assertEquals(77L, out.getId());
        verify(readMapper, never()).insert(any());
    }

    // ---------- readCount ----------

    @Test
    void readCount_returnsCount() {
        PaySlip s = stubSlip(50L, UID, "confirmed");
        when(slipMapper.selectById(50L)).thenReturn(s);
        when(readMapper.countBySlip(50L)).thenReturn(5L);
        assertEquals(5L, service.readCount(50L));
    }

    @Test
    void readCount_crossTenant_throws404() {
        PaySlip other = stubSlip(50L, UID, "confirmed");
        other.setTenantId(999L);
        when(slipMapper.selectById(50L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.readCount(50L));
        assertEquals(404, ex.getCode());
    }

    // ---------- unreadSlips: 员工查自己未读 ----------

    @Test
    void unreadSlips_filtersReadSlips() {
        PaySlip confirmed1 = stubSlip(1L, UID, "confirmed");
        PaySlip confirmed2 = stubSlip(2L, UID, "confirmed");
        when(slipMapper.findByEmployee(UID)).thenReturn(List.of(confirmed1, confirmed2));
        when(readMapper.findBySlipAndEmployee(1L, UID)).thenReturn(new PayPayslipRead()); // 1 已读
        when(readMapper.findBySlipAndEmployee(2L, UID)).thenReturn(null); // 2 未读
        List<PaySlip> out = service.unreadSlips();
        assertEquals(1, out.size());
        assertEquals(2L, out.get(0).getId());
    }

    @Test
    void unreadSlips_skipsDrafts() {
        PaySlip draft = stubSlip(1L, UID, "draft");
        PaySlip confirmed = stubSlip(2L, UID, "confirmed");
        when(slipMapper.findByEmployee(UID)).thenReturn(List.of(draft, confirmed));
        when(readMapper.findBySlipAndEmployee(any(), any())).thenReturn(null);
        List<PaySlip> out = service.unreadSlips();
        assertEquals(1, out.size());
        assertEquals(2L, out.get(0).getId());
    }
}