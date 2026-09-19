package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.SubmitResignationRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrResignation;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrResignationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResignationServiceTest {

    @Mock private HrResignationMapper resignationMapper;
    @Mock private HrEmployeeMapper employeeMapper;

    @InjectMocks private ResignationService resignationService;

    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(100L).tenantId(TID).userName("hr")
            .roles(new HashSet<>(Set.of("hr_admin"))).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private HrEmployee stubEmp(Integer status) {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(TID);
        e.setStatus(status);
        return e;
    }

    @Test
    void submit_happyPath_writesSubmittedStatus() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(EmployeeService.STATUS_ACTIVE));
        when(resignationMapper.insert(any(HrResignation.class))).thenAnswer(inv -> {
            HrResignation r = inv.getArgument(0);
            r.setId(701L);
            return 1;
        });

        SubmitResignationRequest req = new SubmitResignationRequest();
        req.setEmployeeId(50L);
        req.setEffectiveAt(LocalDate.now().plusDays(30));
        req.setReason("personal");

        HrResignation out = resignationService.submit(req);
        assertEquals(701L, out.getId());
        assertEquals(ResignationService.STATUS_SUBMITTED, out.getStatus());
        assertNotNull(out.getSubmitAt());
    }

    @Test
    void submit_alreadyResigned_throws409() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(EmployeeService.STATUS_RESIGNED));
        SubmitResignationRequest req = new SubmitResignationRequest();
        req.setEmployeeId(50L);
        req.setEffectiveAt(LocalDate.now());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resignationService.submit(req));
        assertEquals(409, ex.getCode());
        verify(resignationMapper, never()).insert(any());
    }

    @Test
    void submit_crossTenantEmployee_throws404() {
        HrEmployee e = stubEmp(EmployeeService.STATUS_ACTIVE);
        e.setTenantId(2L);
        when(employeeMapper.selectById(50L)).thenReturn(e);

        SubmitResignationRequest req = new SubmitResignationRequest();
        req.setEmployeeId(50L);
        req.setEffectiveAt(LocalDate.now());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resignationService.submit(req));
        assertEquals(404, ex.getCode());
    }

    @Test
    void revoke_alreadyEffective_throws409() {
        HrResignation r = new HrResignation();
        r.setId(701L);
        r.setTenantId(TID);
        r.setStatus(ResignationService.STATUS_EFFECTIVE);
        when(resignationMapper.selectById(701L)).thenReturn(r);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> resignationService.revoke(701L));
        assertEquals(409, ex.getCode());
        verify(resignationMapper, never()).updateById(any());
    }

    @Test
    void revoke_submitted_flipsToRevoked() {
        HrResignation r = new HrResignation();
        r.setId(701L);
        r.setTenantId(TID);
        r.setStatus(ResignationService.STATUS_SUBMITTED);
        when(resignationMapper.selectById(701L)).thenReturn(r);
        when(resignationMapper.updateById(any(HrResignation.class))).thenAnswer(inv -> 1);

        HrResignation out = resignationService.revoke(701L);
        assertEquals(ResignationService.STATUS_REVOKED, out.getStatus());
    }
}
