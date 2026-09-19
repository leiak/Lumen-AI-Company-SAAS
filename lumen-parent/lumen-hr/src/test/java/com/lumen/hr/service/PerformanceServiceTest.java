package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.StartCycleRequest;
import com.lumen.hr.dto.SubmitPerformanceRequest;
import com.lumen.hr.entity.HrPerformanceCycle;
import com.lumen.hr.entity.HrPerformanceScore;
import com.lumen.hr.mapper.HrPerformanceCycleMapper;
import com.lumen.hr.mapper.HrPerformanceScoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock private HrPerformanceCycleMapper cycleMapper;
    @Mock private HrPerformanceScoreMapper scoreMapper;

    @InjectMocks private PerformanceService performanceService;

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

    private HrPerformanceCycle stubCycle(long id, Integer status) {
        HrPerformanceCycle c = new HrPerformanceCycle();
        c.setId(id);
        c.setTenantId(TID);
        c.setName("2026 H1");
        c.setPeriodStart(LocalDate.of(2026, 1, 1));
        c.setPeriodEnd(LocalDate.of(2026, 6, 30));
        c.setStatus(status);
        return c;
    }

    @Test
    void startCycle_writesRunningStatus() {
        when(cycleMapper.insert(any(HrPerformanceCycle.class))).thenAnswer(inv -> {
            HrPerformanceCycle c = inv.getArgument(0);
            c.setId(3001L);
            return 1;
        });
        StartCycleRequest req = new StartCycleRequest();
        req.setName("2026 H1");
        req.setPeriodStart(LocalDate.of(2026, 1, 1));
        req.setPeriodEnd(LocalDate.of(2026, 6, 30));

        HrPerformanceCycle out = performanceService.startCycle(req);
        assertEquals(PerformanceService.CYCLE_STATUS_RUNNING, out.getStatus());
        assertEquals(TID, out.getTenantId());
    }

    @Test
    void startCycle_invertedDates_throws400() {
        StartCycleRequest req = new StartCycleRequest();
        req.setName("x");
        req.setPeriodStart(LocalDate.of(2026, 7, 1));
        req.setPeriodEnd(LocalDate.of(2026, 1, 1));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> performanceService.startCycle(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void submitScore_rejectsOutOfRange() {
        // Stub the cycle so the request reaches the score-validation branch.
        when(cycleMapper.selectById(1L)).thenReturn(stubCycle(1L, PerformanceService.CYCLE_STATUS_RUNNING));

        SubmitPerformanceRequest req = new SubmitPerformanceRequest();
        req.setCycleId(1L);
        req.setEmployeeId(50L);
        req.setScore(new BigDecimal("150"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> performanceService.submitScore(req));
        assertEquals(400, ex.getCode());
        verify(scoreMapper, never()).insert(any());
    }

    @Test
    void submitScore_happyPath_persistsRoundedScore() {
        when(cycleMapper.selectById(1L)).thenReturn(stubCycle(1L, PerformanceService.CYCLE_STATUS_RUNNING));
        when(scoreMapper.insert(any(HrPerformanceScore.class))).thenAnswer(inv -> {
            HrPerformanceScore s = inv.getArgument(0);
            s.setId(9001L);
            return 1;
        });

        SubmitPerformanceRequest req = new SubmitPerformanceRequest();
        req.setCycleId(1L);
        req.setEmployeeId(50L);
        req.setScore(new BigDecimal("87.555"));
        req.setComment("exceeds expectations");

        HrPerformanceScore out = performanceService.submitScore(req);
        assertEquals(9001L, out.getId());
        assertEquals(0, new BigDecimal("87.56").compareTo(out.getScore()),
            "score must be rounded HALF_UP to 2 decimal places");
    }

    @Test
    void submitScore_closedCycle_throws409() {
        when(cycleMapper.selectById(1L)).thenReturn(stubCycle(1L, PerformanceService.CYCLE_STATUS_CLOSED));

        SubmitPerformanceRequest req = new SubmitPerformanceRequest();
        req.setCycleId(1L);
        req.setEmployeeId(50L);
        req.setScore(new BigDecimal("80"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> performanceService.submitScore(req));
        assertEquals(409, ex.getCode());
    }
}
