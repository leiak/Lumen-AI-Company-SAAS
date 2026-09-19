package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PaySlip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工资条 mapper。UNIQUE(tenant_id, employee_id, period, deleted) 保证一人一期间一条。
 */
@Mapper
public interface PaySlipMapper extends BaseMapper<PaySlip> {

    /**
     * 按 period 列出 (admin 全量视图)。
     */
    @Select("SELECT * FROM pay_slip WHERE period = #{period} AND deleted = 0 ORDER BY id DESC")
    List<PaySlip> findByPeriod(@Param("period") String period);

    /**
     * 按 employee 列出 (含历史)。
     */
    default List<PaySlip> findByEmployee(Long employeeId) {
        return selectList(new LambdaQueryWrapper<PaySlip>()
            .eq(PaySlip::getEmployeeId, employeeId)
            .eq(PaySlip::getDeleted, 0)
            .orderByDesc(PaySlip::getPeriod));
    }

    /**
     * 按 employee + period 查一条 (UNIQUE 校验)。
     */
    default PaySlip findByEmployeeAndPeriod(Long employeeId, String period) {
        return selectOne(new LambdaQueryWrapper<PaySlip>()
            .eq(PaySlip::getEmployeeId, employeeId)
            .eq(PaySlip::getPeriod, period)
            .eq(PaySlip::getDeleted, 0));
    }

    /**
     * 列出某员工某期间内的所有 slip (用于查未读等)。
     */
    default List<PaySlip> findByEmployeeAndPeriodList(Long employeeId, String period) {
        return selectList(new LambdaQueryWrapper<PaySlip>()
            .eq(PaySlip::getEmployeeId, employeeId)
            .eq(PaySlip::getPeriod, period)
            .eq(PaySlip::getDeleted, 0));
    }
}