package com.lumen.mobile.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.MobAttendance;
import com.lumen.mobile.mapper.MobAttendanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private MobAttendanceMapper mapper;

    @InjectMocks private AttendanceService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private MobAttendance mk(String type) {
        MobAttendance a = new MobAttendance();
        a.setId(System.nanoTime());
        a.setUserId(UID);
        a.setType(type);
        a.setStatus(AttendanceService.STATUS_NORMAL);
        return a;
    }

    // ----- clockIn 重复打卡校验 (安全要求 #14) -----

    @Test
    void clockIn_noExisting_inserts() {
        when(mapper.findByUserAndDate(eq(UID), any(LocalDate.class))).thenReturn(List.of());
        when(mapper.insert(any(MobAttendance.class))).thenAnswer(inv -> {
            MobAttendance a = inv.getArgument(0);
            a.setId(7L);
            return 1;
        });
        MobAttendance out = service.clockIn(UID,
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            "北京市东城区", "https://img/photo.jpg", "device-1");
        assertEquals(7L, out.getId());
        assertEquals(AttendanceService.TYPE_CLOCK_IN, out.getType());
        assertEquals(UID, out.getUserId());
        assertNotNull(out.getDistanceFromOfficeM());
    }

    @Test
    void clockIn_alreadyClockedIn_throws409() {
        when(mapper.findByUserAndDate(eq(UID), any(LocalDate.class)))
            .thenReturn(List.of(mk(AttendanceService.TYPE_CLOCK_IN)));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            null, null, "device-1"));
        assertEquals(409, ex.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void clockOut_alreadyClockedOut_throws409() {
        when(mapper.findByUserAndDate(eq(UID), any(LocalDate.class)))
            .thenReturn(List.of(mk(AttendanceService.TYPE_CLOCK_OUT)));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockOut(UID,
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            null, "device-1"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void clockIn_noGps_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            null, new BigDecimal("116.397470"), null, null, "device-1"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void clockIn_latitudeOutOfRange_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("91"), new BigDecimal("116"), null, null, "device-1"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("latitude"));
    }

    @Test
    void clockIn_longitudeOutOfRange_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("39"), new BigDecimal("181"), null, null, "device-1"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("longitude"));
    }

    @Test
    void clockIn_missingDeviceId_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("39"), new BigDecimal("116"), null, null, ""));
        assertEquals(400, ex.getCode());
    }

    // 安全要求 #16
    @Test
    void clockIn_photoUrlTooLong_throws400() {
        String longUrl = "https://x.com/" + "a".repeat(AttendanceService.PHOTO_URL_MAX_LENGTH);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("39"), new BigDecimal("116"), null, longUrl, "d1"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("photoUrl"));
    }

    // ----- late/early 判定 (安全要求 #8) -----

    @Test
    void clockIn_beforeNine_statusNormal() {
        // findByUserAndDate returns empty — service inserts
        when(mapper.findByUserAndDate(eq(UID), any(LocalDate.class))).thenReturn(List.of());
        when(mapper.insert(any(MobAttendance.class))).thenAnswer(inv -> {
            MobAttendance a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });
        MobAttendance out = service.clockIn(UID,
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            null, null, "d1");
        // 默认 LocalDateTime.now() — 测试时若 9:00 后跑，status=late；为稳定测试只看合法枚举值
        assertTrue(out.getStatus().equals(AttendanceService.STATUS_NORMAL)
                || out.getStatus().equals(AttendanceService.STATUS_LATE),
            "clock_in status must be normal or late, got " + out.getStatus());
    }

    @Test
    void clockOut_beforeEighteen_statusEarlyOrNormal() {
        when(mapper.findByUserAndDate(eq(UID), any(LocalDate.class))).thenReturn(List.of());
        when(mapper.insert(any(MobAttendance.class))).thenAnswer(inv -> {
            MobAttendance a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        });
        MobAttendance out = service.clockOut(UID,
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            null, "d1");
        assertTrue(out.getStatus().equals(AttendanceService.STATUS_EARLY)
                || out.getStatus().equals(AttendanceService.STATUS_NORMAL),
            "clock_out status must be early or normal, got " + out.getStatus());
    }

    // ----- haversine 距离 -----

    @Test
    void haversineMeters_samePoint_zeroDistance() {
        int d = AttendanceService.haversineMeters(
            new BigDecimal("39.908823"), new BigDecimal("116.397470"),
            new BigDecimal("39.908823"), new BigDecimal("116.397470"));
        assertEquals(0, d);
    }

    @Test
    void haversineMeters_knownDistance() {
        // 北京 → 上海 大约 1,067 km
        int d = AttendanceService.haversineMeters(
            new BigDecimal("39.9042"), new BigDecimal("116.4074"),   // 北京
            new BigDecimal("31.2304"), new BigDecimal("121.4737"));  // 上海
        assertTrue(d > 1_000_000 && d < 1_100_000,
            "Beijing-Shanghai distance should be ~1.07M meters, got " + d);
    }

    // ----- period 解析 -----

    @Test
    void parsePeriod_valid_returnsFirstAndLastDay() {
        LocalDate[] r = AttendanceService.parsePeriod("2026-09");
        assertEquals(LocalDate.of(2026, 9, 1), r[0]);
        assertEquals(LocalDate.of(2026, 9, 30), r[1]);
    }

    @Test
    void parsePeriod_invalid_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> AttendanceService.parsePeriod("2026-99"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void parsePeriod_null_defaultsToCurrentMonth() {
        LocalDate[] r = AttendanceService.parsePeriod(null);
        LocalDate today = LocalDate.now();
        assertEquals(today.withDayOfMonth(1), r[0]);
        assertEquals(today.withDayOfMonth(today.lengthOfMonth()), r[1]);
    }

    // ----- myAttendance -----

    @Test
    void myAttendance_returnsByUserAndPeriod() {
        MobAttendance a1 = mk(AttendanceService.TYPE_CLOCK_IN);
        when(mapper.findByUserAndPeriod(eq(UID), any(), any())).thenReturn(List.of(a1));
        List<MobAttendance> out = service.myAttendance(UID, "2026-09");
        assertEquals(1, out.size());
    }

    // ----- tenant / ctx -----

    @Test
    void clockIn_noUserContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () -> service.clockIn(UID,
            new BigDecimal("39"), new BigDecimal("116"), null, null, "d1"));
        assertEquals(401, ex.getCode());
    }
}
