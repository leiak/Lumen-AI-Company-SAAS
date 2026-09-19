package com.lumen.mobile.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.mobile.entity.MobAttendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤打卡 mapper。
 *
 * <p>{@code findByUserAndDate} 用于 service 层 clockIn/clockOut 防重复校验（UNIQUE 兜底）。</p>
 * <p>{@code findLate} 用于 HR 报表（迟到名单）。</p>
 */
@Mapper
public interface MobAttendanceMapper extends BaseMapper<MobAttendance> {

    /**
     * 用户 + 日期当天的全部打卡（含 clock_in / clock_out）。
     */
    @Select("SELECT * FROM mob_attendance WHERE user_id = #{userId} "
        + "AND DATE(clocked_at) = #{date} AND deleted = 0 ORDER BY clocked_at ASC")
    List<MobAttendance> findByUserAndDate(@Param("userId") Long userId,
                                          @Param("date") LocalDate date);

    /**
     * 用户 + 起止时间范围（用于 myAttendance / period 查询）。
     */
    default List<MobAttendance> findByUserAndPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return selectList(new LambdaQueryWrapper<MobAttendance>()
            .eq(MobAttendance::getUserId, userId)
            .between(MobAttendance::getClockedAt, start, end)
            .eq(MobAttendance::getDeleted, 0)
            .orderByDesc(MobAttendance::getClockedAt));
    }

    /**
     * 部门下迟到打卡列表（status=late）。
     * 注：HR 报表用，service 层按 tenantId + deptId 过滤（deptId 通过 user.deptId 关联，
     * 真实 SQL 走 JOIN，此处简化 — TODO P5 加 hr_employee JOIN）。
     */
    @Select("SELECT * FROM mob_attendance WHERE status = 'late' AND deleted = 0 "
        + "ORDER BY clocked_at DESC LIMIT 500")
    List<MobAttendance> findLate();
}
