package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrOnboarding;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrOnboardingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private HrOnboardingMapper onboardingMapper;
    @Mock private HrEmployeeMapper employeeMapper;

    @InjectMocks private OnboardingService onboardingService;

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

    @Test
    void start_createsChecklistAndInProgress() {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(TID);
        when(employeeMapper.selectById(50L)).thenReturn(e);
        when(onboardingMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(null);
        when(onboardingMapper.insert(any(HrOnboarding.class))).thenAnswer(inv -> {
            HrOnboarding ob = inv.getArgument(0);
            ob.setId(8001L);
            return 1;
        });

        HrOnboarding out = onboardingService.start(50L);
        assertEquals(8001L, out.getId());
        assertEquals(OnboardingService.STATUS_IN_PROGRESS, out.getStatus());
        assertNotNull(out.getChecklist());
        assertFalse(out.getChecklist().isEmpty());
        // Every default item should start as false (pending).
        out.getChecklist().values().forEach(v -> assertFalse(v));
    }

    @Test
    void complete_setsCompletedAt() {
        HrOnboarding existing = new HrOnboarding();
        existing.setId(8001L);
        existing.setTenantId(TID);
        existing.setStatus(OnboardingService.STATUS_IN_PROGRESS);
        when(onboardingMapper.selectById(8001L)).thenReturn(existing);
        when(onboardingMapper.updateById(any(HrOnboarding.class))).thenAnswer(inv -> 1);

        HrOnboarding out = onboardingService.complete(8001L);
        assertEquals(OnboardingService.STATUS_COMPLETED, out.getStatus());
        assertNotNull(out.getCompletedAt());
    }

    @Test
    void complete_alreadyCompleted_throws409() {
        HrOnboarding existing = new HrOnboarding();
        existing.setId(8001L);
        existing.setTenantId(TID);
        existing.setStatus(OnboardingService.STATUS_COMPLETED);
        when(onboardingMapper.selectById(8001L)).thenReturn(existing);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> onboardingService.complete(8001L));
        assertEquals(409, ex.getCode());
        verify(onboardingMapper, never()).updateById(any());
    }

    @Test
    void start_crossTenantEmployee_returns404() {
        HrEmployee e = new HrEmployee();
        e.setId(50L);
        e.setTenantId(2L);
        when(employeeMapper.selectById(50L)).thenReturn(e);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> onboardingService.start(50L));
        assertEquals(404, ex.getCode());
        verify(onboardingMapper, never()).insert(any());
    }
}
