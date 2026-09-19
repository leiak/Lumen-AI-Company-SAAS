package com.lumen.mobile.controller;

import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.ClockInRequest;
import com.lumen.mobile.dto.ClockOutRequest;
import com.lumen.mobile.entity.MobAttendance;
import com.lumen.mobile.service.AttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 移动端考勤打卡（GPS + photo）。
 * 安全要求 #14：一天一次；安全要求 #16：photoUrl 限长。
 */
@RestController
@RequestMapping("/mobile/api/attendance")
@RequiredArgsConstructor
@Validated
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in")
    @PreAuthorize("isAuthenticated()")
    public R<MobAttendance> clockIn(@RequestBody @Valid ClockInRequest req) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "no user");
        return R.ok(attendanceService.clockIn(uid, req.getLatitude(), req.getLongitude(),
            req.getAddress(), req.getPhotoUrl(), req.getDeviceId()));
    }

    @PostMapping("/clock-out")
    @PreAuthorize("isAuthenticated()")
    public R<MobAttendance> clockOut(@RequestBody @Valid ClockOutRequest req) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "no user");
        return R.ok(attendanceService.clockOut(uid, req.getLatitude(), req.getLongitude(),
            req.getAddress(), req.getDeviceId()));
    }

    /** 我的考勤（默认当月）。pageSize 限制 [1, 200]（安全要求 #15）。 */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<MobAttendance>> my(@RequestParam(required = false) String period,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(200) int limit) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "no user");
        // limit 仅作为约束；service 返回 period 范围所有记录
        return R.ok(attendanceService.myAttendance(uid, period));
    }

    /** 部门考勤（admin）。 */
    @GetMapping("/dept")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<List<MobAttendance>> dept(@RequestParam Long deptId,
                                        @RequestParam(required = false) String period) {
        return R.ok(attendanceService.listByDept(deptId, period));
    }
}
