package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinBudget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinBudgetMapper extends BaseMapper<FinBudget> {

    /**
     * 按 period + deptId 查预算列表。
     */
    @Select("SELECT * FROM fin_budget WHERE period = #{period} AND department_id = #{deptId} "
        + "AND deleted = 0 ORDER BY id")
    List<FinBudget> findByPeriodAndDept(@Param("period") String period,
                                       @Param("deptId") Long deptId);

    /**
     * 查 (period, dept, subject) 唯一预算行 —— 用于 BudgetService.consume。
     */
    @Select("SELECT * FROM fin_budget WHERE period = #{period} AND department_id = #{deptId} "
        + "AND subject_id = #{subjectId} AND deleted = 0 LIMIT 1")
    FinBudget findOne(@Param("period") String period,
                      @Param("deptId") Long deptId,
                      @Param("subjectId") Long subjectId);
}
