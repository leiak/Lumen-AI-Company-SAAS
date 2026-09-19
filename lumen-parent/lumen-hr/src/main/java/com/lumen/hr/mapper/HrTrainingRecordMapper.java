package com.lumen.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.hr.entity.HrTrainingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HrTrainingRecordMapper extends BaseMapper<HrTrainingRecord> {

    /**
     * 查询某员工的所有培训记录。
     * 租户隔离由 TenantLineInnerInterceptor 自动注入。
     */
    default List<HrTrainingRecord> findByEmployee(@Param("employeeId") Long employeeId) {
        return selectList(new LambdaQueryWrapper<HrTrainingRecord>()
            .eq(HrTrainingRecord::getEmployeeId, employeeId)
            .orderByDesc(HrTrainingRecord::getCompletedAt));
    }
}
