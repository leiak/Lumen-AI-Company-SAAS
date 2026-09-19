package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.TransferRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrTransfer;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrTransferMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private HrTransferMapper transferMapper;
    @Mock private HrEmployeeMapper employeeMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private EmployeeService employeeService;
    private TransferService transferService;

    private static final long TID = 1L;
    private static final long OTHER_TID = 2L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).userName("hr")
            .roles(new HashSet<>(Set.of("hr_admin"))).build());
        transferService = new TransferService(transferMapper, employeeMapper, employeeService);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void approve_delegatesToEmployeeServiceTransfer() {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(TID);
        e.setDeptId(10L);
        e.setPostId(20L);
        when(employeeMapper.selectById(50L)).thenReturn(e);
        when(transferMapper.insert(any(HrTransfer.class))).thenAnswer(inv -> {
            HrTransfer t = inv.getArgument(0);
            t.setId(7001L);
            return 1;
        });
        when(employeeMapper.updateById(any(HrEmployee.class))).thenAnswer(inv -> 1);

        TransferRequest req = new TransferRequest();
        req.setEmployeeId(50L);
        req.setToDeptId(99L);
        req.setToPostId(88L);
        req.setEffectiveAt(LocalDate.of(2026, 11, 1));

        HrEmployee out = transferService.approve(req);
        assertEquals(99L, out.getDeptId());
        assertEquals(88L, out.getPostId());
        verify(transferMapper).insert(any(HrTransfer.class));
    }

    @Test
    void history_returnsTransferRecords() {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(TID);
        when(employeeMapper.selectById(50L)).thenReturn(e);
        when(transferMapper.findByEmployee(eq(50L))).thenReturn(List.of());

        List<HrTransfer> out = transferService.history(50L);
        assertNotNull(out);
        verify(transferMapper).findByEmployee(50L);
    }

    @Test
    void history_crossTenantEmployee_returns404() {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(OTHER_TID);
        when(employeeMapper.selectById(50L)).thenReturn(e);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> transferService.history(50L));
        assertEquals(404, ex.getCode());
        verify(transferMapper, never()).findByEmployee(any());
    }
}
