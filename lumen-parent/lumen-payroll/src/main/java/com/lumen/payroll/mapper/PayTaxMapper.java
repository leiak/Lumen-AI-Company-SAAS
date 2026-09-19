package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PayTax;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 个税 mapper。
 */
@Mapper
public interface PayTaxMapper extends BaseMapper<PayTax> {

    /**
     * 按 employee + period 查。
     */
    default PayTax findByEmployeeAndPeriod(Long employeeId, String period) {
        return selectOne(new LambdaQueryWrapper<PayTax>()
            .eq(PayTax::getEmployeeId, employeeId)
            .eq(PayTax::getPeriod, period)
            .eq(PayTax::getDeleted, 0));
    }

    /**
     * 按 slip 查。
     */
    default PayTax findBySlip(Long slipId) {
        return selectOne(new LambdaQueryWrapper<PayTax>()
            .eq(PayTax::getSlipId, slipId)
            .eq(PayTax::getDeleted, 0));
    }

    /**
     * 列出某员工从当年 1 月到指定 period 的累计 (含当前 period)。
     */
    default List<PayTax> listByEmployeeYearTo(Long employeeId, String periodStartInclusive, String periodEndInclusive) {
        return selectList(new LambdaQueryWrapper<PayTax>()
            .eq(PayTax::getEmployeeId, employeeId)
            .ge(PayTax::getPeriod, periodStartInclusive)
            .le(PayTax::getPeriod, periodEndInclusive)
            .eq(PayTax::getDeleted, 0)
            .orderByAsc(PayTax::getPeriod));
    }
}