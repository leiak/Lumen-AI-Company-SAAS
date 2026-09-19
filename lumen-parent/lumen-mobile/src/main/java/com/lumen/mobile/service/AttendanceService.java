package com.lumen.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.MobAttendance;
import com.lumen.mobile.mapper.MobAttendanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 移动端考勤打卡服务。
 *
 * <p>安全要求 #7：GPS 范围校验（TODO P5 — 本地 stub）。
 * 安全要求 #8：late / early 判定（9:00 前正常，后迟到；18:00 后算加班 — 规则配置 TODO）。
 * 安全要求 #14：一天只能打卡一次（type + DATE(clocked_at) UNIQUE）。
 * 安全要求 #16：photoUrl VARCHAR(512)，超长拒绝。</p>
 *
 * <p>distanceFromOfficeM 由 service 计算（Haversine 简化版 — TODO P5 用真实 GIS）。
 * status 默认 normal；clock_in 在 9:00 后判定为 late；clock_out 在 18:00 前判定为 early。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final String TYPE_CLOCK_IN = "clock_in";
    public static final String TYPE_CLOCK_OUT = "clock_out";

    public static final String STATUS_NORMAL = "normal";
    public static final String STATUS_LATE = "late";
    public static final String STATUS_EARLY = "early";
    public static final String STATUS_ABSENT = "absent";

    /** 上班截止时间（9:00 后判定为迟到）。规则配置 TODO。 */
    public static final LocalTime CLOCK_IN_DEADLINE = LocalTime.of(9, 0);

    /** 下班时间（18:00 前判定为早退）。 */
    public static final LocalTime CLOCK_OUT_THRESHOLD = LocalTime.of(18, 0);

    /** 打卡照片 URL 长度上限（VARCHAR(512)）。 */
    public static final int PHOTO_URL_MAX_LENGTH = 512;

    /** 办公室坐标（TODO P5 从租户配置或 HR 表读取）。 */
    public static final BigDecimal OFFICE_LATITUDE = new BigDecimal("39.908823");
    public static final BigDecimal OFFICE_LONGITUDE = new BigDecimal("116.397470");

    /** GPS 范围半径（米）。超过此距离允许但 distance 记录，服务端可基于 distanceFromOfficeM 二次校验。 */
    public static final int OFFICE_RADIUS_METERS = 500;

    private final MobAttendanceMapper attendanceMapper;

    @Transactional
    public MobAttendance clockIn(Long userId, BigDecimal latitude, BigDecimal longitude,
                                  String address, String photoUrl, String deviceId) {
        requireUserCtx();
        validateGps(latitude, longitude);
        validatePhotoUrl(photoUrl);
        validateDeviceId(deviceId);

        // 安全要求 #14: 一天一次 (clock_in) — service 预校验
        LocalDate today = LocalDate.now();
        List<MobAttendance> existing = attendanceMapper.findByUserAndDate(userId, today);
        for (MobAttendance a : existing) {
            if (TYPE_CLOCK_IN.equals(a.getType())) {
                throw new ServiceException(409, "Already clocked in today");
            }
        }

        MobAttendance a = new MobAttendance();
        a.setUserId(userId);
        a.setType(TYPE_CLOCK_IN);
        a.setLatitude(latitude);
        a.setLongitude(longitude);
        a.setAddress(address);
        a.setPhotoUrl(photoUrl);
        a.setDeviceId(deviceId);
        a.setDistanceFromOfficeM(haversineMeters(latitude, longitude, OFFICE_LATITUDE, OFFICE_LONGITUDE));
        a.setClockedAt(LocalDateTime.now());
        a.setStatus(determineClockInStatus(a.getClockedAt()));
        attendanceMapper.insert(a);
        log.info("Clock-in userId={} distanceFromOfficeM={} status={}",
            userId, a.getDistanceFromOfficeM(), a.getStatus());
        return a;
    }

    @Transactional
    public MobAttendance clockOut(Long userId, BigDecimal latitude, BigDecimal longitude,
                                   String address, String deviceId) {
        requireUserCtx();
        validateGps(latitude, longitude);
        validateDeviceId(deviceId);

        LocalDate today = LocalDate.now();
        List<MobAttendance> existing = attendanceMapper.findByUserAndDate(userId, today);
        for (MobAttendance a : existing) {
            if (TYPE_CLOCK_OUT.equals(a.getType())) {
                throw new ServiceException(409, "Already clocked out today");
            }
        }

        MobAttendance a = new MobAttendance();
        a.setUserId(userId);
        a.setType(TYPE_CLOCK_OUT);
        a.setLatitude(latitude);
        a.setLongitude(longitude);
        a.setAddress(address);
        a.setDeviceId(deviceId);
        a.setDistanceFromOfficeM(haversineMeters(latitude, longitude, OFFICE_LATITUDE, OFFICE_LONGITUDE));
        a.setClockedAt(LocalDateTime.now());
        a.setStatus(determineClockOutStatus(a.getClockedAt()));
        attendanceMapper.insert(a);
        log.info("Clock-out userId={} distanceFromOfficeM={} status={}",
            userId, a.getDistanceFromOfficeM(), a.getStatus());
        return a;
    }

    /**
     * 我的考勤（period=yyyy-MM）。
     */
    public List<MobAttendance> myAttendance(Long userId, String period) {
        requireUserCtx();
        LocalDate[] range = parsePeriod(period);
        return attendanceMapper.findByUserAndPeriod(userId,
            range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay().minusNanos(1));
    }

    /**
     * 部门考勤列表（admin only，period=yyyy-MM）。
     * 注意：deptId 通过 user.deptId 关联 HR 员工表 — 这里简化：先按 tenant 返回所有记录，
     * 真实 SQL 走 JOIN（TODO P5 加 hr_employee JOIN）。
     */
    public List<MobAttendance> listByDept(Long deptId, String period) {
        requireAdminCtx();
        if (deptId == null) throw new ServiceException(400, "deptId is required");
        // TODO P5: 关联 hr_employee 拿 deptId
        LocalDate[] range = parsePeriod(period);
        var w = new LambdaQueryWrapper<MobAttendance>()
            .eq(MobAttendance::getDeleted, 0)
            .between(MobAttendance::getClockedAt,
                range[0].atStartOfDay(),
                range[1].plusDays(1).atStartOfDay().minusNanos(1))
            .orderByDesc(MobAttendance::getClockedAt);
        return attendanceMapper.selectList(w);
    }

    /**
     * 9:00 前 normal；之后 late（安全要求 #8）。
     */
    private static String determineClockInStatus(LocalDateTime when) {
        return when.toLocalTime().isAfter(CLOCK_IN_DEADLINE) ? STATUS_LATE : STATUS_NORMAL;
    }

    /**
     * 18:00 前 early；之后 normal（加班 = normal）。
     */
    private static String determineClockOutStatus(LocalDateTime when) {
        return when.toLocalTime().isBefore(CLOCK_OUT_THRESHOLD) ? STATUS_EARLY : STATUS_NORMAL;
    }

    private void validateGps(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            throw new ServiceException(400, "latitude/longitude are required");
        }
        if (lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new ServiceException(400, "latitude out of range [-90,90]");
        }
        if (lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new ServiceException(400, "longitude out of range [-180,180]");
        }
        // 安全要求 #7: GPS 范围校验 TODO P5 — 当前只记录 distance，service 不直接拒绝（允许远程打卡）
    }

    private void validatePhotoUrl(String photoUrl) {
        if (photoUrl == null) return;
        if (photoUrl.length() > PHOTO_URL_MAX_LENGTH) {
            throw new ServiceException(400,
                "photoUrl too long (max " + PHOTO_URL_MAX_LENGTH + ")");
        }
    }

    private void validateDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new ServiceException(400, "deviceId is required");
        }
    }

    /**
     * Haversine 距离（米），简化版。
     */
    static int haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double R = 6_371_000.0;
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLam = Math.toRadians(lng2.subtract(lng1).doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLam / 2) * Math.sin(dLam / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
    }

    static LocalDate[] parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            // 默认当月
            LocalDate today = LocalDate.now();
            LocalDate first = today.withDayOfMonth(1);
            LocalDate last = today.withDayOfMonth(today.lengthOfMonth());
            return new LocalDate[] { first, last };
        }
        java.time.format.DateTimeFormatter ymFmt =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        java.time.format.DateTimeFormatter ymdFmt =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            LocalDate first;
            if (period.length() == 7) {
                first = LocalDate.parse(period + "-01", ymdFmt);
            } else {
                first = LocalDate.parse(period, ymdFmt);
            }
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            return new LocalDate[] { first, last };
        } catch (Exception e) {
            try {
                YearMonth ym = YearMonth.parse(period, ymFmt);
                LocalDate first = ym.atDay(1);
                LocalDate last = ym.atEndOfMonth();
                return new LocalDate[] { first, last };
            } catch (Exception inner) {
                throw new ServiceException(400, "period must be yyyy-MM format");
            }
        }
    }

    private UserContext requireUserCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "Missing user context");
        }
        return ctx;
    }

    private UserContext requireAdminCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }
}
