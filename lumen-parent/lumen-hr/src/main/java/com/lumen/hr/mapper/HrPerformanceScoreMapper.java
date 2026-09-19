package com.lumen.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.hr.entity.HrPerformanceScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HrPerformanceScoreMapper extends BaseMapper<HrPerformanceScore> {

    /**
     * 按周期 + 员工查询评分（一人可能多条历史）。
     * 租户隔离由 TenantLineInnerInterceptor 自动注入。
     */
    default List<HrPerformanceScore> findByCycleAndEmployee(@Param("cycleId") Long cycleId,
                                                           @Param("employeeId") Long employeeId) {
        return selectList(new LambdaQueryWrapper<HrPerformanceScore>()
            .eq(HrPerformanceScore::getCycleId, cycleId)
            .eq(HrPerformanceScore::getEmployeeId, employeeId)
            .orderByDesc(HrPerformanceScore::getSubmittedAt));
    }
}
