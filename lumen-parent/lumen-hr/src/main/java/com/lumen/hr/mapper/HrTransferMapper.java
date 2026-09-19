package com.lumen.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.hr.entity.HrTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HrTransferMapper extends BaseMapper<HrTransfer> {

    /**
     * 历史调岗记录（按生效日期倒序）。
     * 租户隔离由 TenantLineInnerInterceptor 自动注入。
     */
    default List<HrTransfer> findByEmployee(@Param("employeeId") Long employeeId) {
        return selectList(new LambdaQueryWrapper<HrTransfer>()
            .eq(HrTransfer::getEmployeeId, employeeId)
            .orderByDesc(HrTransfer::getEffectiveAt));
    }
}
