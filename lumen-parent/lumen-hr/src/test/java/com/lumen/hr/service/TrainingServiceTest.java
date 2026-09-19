package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.CompleteTrainingRequest;
import com.lumen.hr.dto.CreateTrainingPlanRequest;
import com.lumen.hr.entity.HrTrainingPlan;
import com.lumen.hr.entity.HrTrainingRecord;
import com.lumen.hr.mapper.HrTrainingPlanMapper;
import com.lumen.hr.mapper.HrTrainingRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {

    @Mock private HrTrainingPlanMapper planMapper;
    @Mock private HrTrainingRecordMapper recordMapper;

    @InjectMocks private TrainingService trainingService;

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
    void createPlan_persistsDraftPlan() {
        when(planMapper.insert(any(HrTrainingPlan.class))).thenAnswer(inv -> {
            HrTrainingPlan p = inv.getArgument(0);
            p.setId(7001L);
            return 1;
        });

        CreateTrainingPlanRequest req = new CreateTrainingPlanRequest();
        req.setName("新员工培训");
        req.setStartAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        req.setEndAt(LocalDateTime.of(2026, 10, 1, 17, 0));
        req.setCapacity(20);

        HrTrainingPlan out = trainingService.createPlan(req);
        assertEquals(7001L, out.getId());
        assertEquals(TrainingService.PLAN_STATUS_DRAFT, out.getStatus());
        assertEquals(20, out.getCapacity());
    }

    @Test
    void createPlan_invertedWindow_throws400() {
        CreateTrainingPlanRequest req = new CreateTrainingPlanRequest();
        req.setName("x");
        req.setStartAt(LocalDateTime.of(2026, 10, 1, 17, 0));
        req.setEndAt(LocalDateTime.of(2026, 10, 1, 9, 0));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> trainingService.createPlan(req));
        assertEquals(400, ex.getCode());
        verify(planMapper, never()).insert(any());
    }

    @Test
    void enroll_insertsRecord() {
        HrTrainingPlan p = new HrTrainingPlan();
        p.setId(10L);
        p.setTenantId(TID);
        when(planMapper.selectById(10L)).thenReturn(p);
        when(recordMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(null);
        when(recordMapper.insert(any(HrTrainingRecord.class))).thenAnswer(inv -> {
            HrTrainingRecord r = inv.getArgument(0);
            r.setId(8001L);
            return 1;
        });

        HrTrainingRecord out = trainingService.enroll(10L, 50L);
        assertEquals(8001L, out.getId());
        assertNull(out.getCompletedAt(), "fresh enrollment has no completion time");
    }

    @Test
    void complete_setsCompletedAtAndScore() {
        HrTrainingRecord existing = new HrTrainingRecord();
        existing.setId(8001L);
        existing.setTenantId(TID);
        existing.setPlanId(10L);
        existing.setEmployeeId(50L);
        when(recordMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(existing);
        when(recordMapper.updateById(any(HrTrainingRecord.class))).thenAnswer(inv -> 1);

        CompleteTrainingRequest req = new CompleteTrainingRequest();
        req.setPlanId(10L);
        req.setEmployeeId(50L);
        req.setScore(new BigDecimal("92.50"));

        HrTrainingRecord out = trainingService.complete(req);
        assertNotNull(out.getCompletedAt());
        assertEquals(0, new BigDecimal("92.50").compareTo(out.getScore()));
    }

    @Test
    void complete_noEnrollment_throws404() {
        when(recordMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(null);

        CompleteTrainingRequest req = new CompleteTrainingRequest();
        req.setPlanId(10L);
        req.setEmployeeId(50L);
        req.setScore(new BigDecimal("80"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> trainingService.complete(req));
        assertEquals(404, ex.getCode());
        verify(recordMapper, never()).updateById(any());
    }
}
