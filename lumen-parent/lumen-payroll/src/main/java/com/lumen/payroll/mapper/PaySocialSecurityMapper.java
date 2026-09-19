package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PaySocialSecurity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 社保 mapper。
 */
@Mapper
public interface PaySocialSecurityMapper extends BaseMapper<PaySocialSecurity> {

    /**
     * 按 employee + period 查唯一。
     */
    default PaySocialSecurity findByEmployeeAndPeriod(Long employeeId, String period) {
        return selectOne(new LambdaQueryWrapper<PaySocialSecurity>()
            .eq(PaySocialSecurity::getEmployeeId, employeeId)
            .eq(PaySocialSecurity::getPeriod, period)
            .eq(PaySocialSecurity::getDeleted, 0));
    }

    /**
     * 列出整月所有员工的社保记录。
     */
    @Select("SELECT * FROM pay_social_security WHERE period = #{period} AND deleted = 0 ORDER BY employee_id ASC")
    List<PaySocialSecurity> findByPeriod(@Param("period") String period);
}