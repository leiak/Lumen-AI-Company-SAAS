package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PayEmployeeSalary;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工薪资 mapper。
 */
@Mapper
public interface PayEmployeeSalaryMapper extends BaseMapper<PayEmployeeSalary> {

    /**
     * 按 employee 列出所有历史薪资记录。
     */
    default List<PayEmployeeSalary> findByEmployee(Long employeeId) {
        return selectList(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getEmployeeId, employeeId)
            .eq(PayEmployeeSalary::getDeleted, 0)
            .orderByDesc(PayEmployeeSalary::getEffectiveFrom));
    }

    /**
     * 列出某员工所有 active 薪资记录 (用于算薪)。
     */
    default List<PayEmployeeSalary> findActiveByEmployee(Long employeeId) {
        return selectList(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getEmployeeId, employeeId)
            .eq(PayEmployeeSalary::getStatus, "active")
            .eq(PayEmployeeSalary::getDeleted, 0));
    }

    /**
     * 找生效日期 <= date 的最近一条 active 记录。
     */
    default PayEmployeeSalary findByEmployeeAndEffectiveAt(Long employeeId, LocalDate date) {
        return selectOne(new LambdaQueryWrapper<PayEmployeeSalary>()
            .eq(PayEmployeeSalary::getEmployeeId, employeeId)
            .eq(PayEmployeeSalary::getStatus, "active")
            .le(PayEmployeeSalary::getEffectiveFrom, date)
            .and(w -> w.isNull(PayEmployeeSalary::getEffectiveTo)
                .or().ge(PayEmployeeSalary::getEffectiveTo, date))
            .eq(PayEmployeeSalary::getDeleted, 0)
            .orderByDesc(PayEmployeeSalary::getEffectiveFrom)
            .last("LIMIT 1"));
    }
}