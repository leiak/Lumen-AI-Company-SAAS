package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PaySalaryStructure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 薪资结构 mapper。
 */
@Mapper
public interface PaySalaryStructureMapper extends BaseMapper<PaySalaryStructure> {

    /**
     * 按 code 查唯一 (租户内)。
     */
    default PaySalaryStructure findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<PaySalaryStructure>()
            .eq(PaySalaryStructure::getCode, code)
            .eq(PaySalaryStructure::getDeleted, 0));
    }

    /**
     * 列出所有 active 结构。
     */
    @Select("SELECT * FROM pay_salary_structure WHERE status = 'active' AND deleted = 0 ORDER BY id DESC")
    List<PaySalaryStructure> findActive();

    /**
     * 列出所有未删除结构（按租户过滤交给 service）。
     */
    @Select("SELECT * FROM pay_salary_structure WHERE deleted = 0 ORDER BY id DESC")
    List<PaySalaryStructure> findAll();

    /**
     * 按 id + 租户过滤查询（service 层 tenant 校验）。
     */
    default PaySalaryStructure findByCodeAndTenant(String code, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<PaySalaryStructure>()
            .eq(PaySalaryStructure::getCode, code)
            .eq(PaySalaryStructure::getTenantId, tenantId)
            .eq(PaySalaryStructure::getDeleted, 0));
    }
}