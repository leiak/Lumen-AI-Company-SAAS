package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PayPayslipRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工资条已读 mapper。
 */
@Mapper
public interface PayPayslipReadMapper extends BaseMapper<PayPayslipRead> {

    /**
     * 按 slip 列出所有已读记录 (用于 readCount + list)。
     */
    default List<PayPayslipRead> findBySlip(Long slipId) {
        return selectList(new LambdaQueryWrapper<PayPayslipRead>()
            .eq(PayPayslipRead::getSlipId, slipId)
            .eq(PayPayslipRead::getDeleted, 0));
    }

    /**
     * 按 slip 统计已读人数。
     */
    @Select("SELECT COUNT(*) FROM pay_payslip_read WHERE slip_id = #{slipId} AND deleted = 0")
    long countBySlip(@Param("slipId") Long slipId);

    /**
     * 按 (slip, employee) 查唯一 (用于幂等标记已读)。
     */
    default PayPayslipRead findBySlipAndEmployee(Long slipId, Long employeeId) {
        return selectOne(new LambdaQueryWrapper<PayPayslipRead>()
            .eq(PayPayslipRead::getSlipId, slipId)
            .eq(PayPayslipRead::getEmployeeId, employeeId)
            .eq(PayPayslipRead::getDeleted, 0));
    }
}