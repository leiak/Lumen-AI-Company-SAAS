package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinBudgetItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinBudgetItemMapper extends BaseMapper<FinBudgetItem> {

    @Select("SELECT * FROM fin_budget_item WHERE budget_id = #{budgetId} AND deleted = 0 ORDER BY id")
    List<FinBudgetItem> listByBudget(@Param("budgetId") Long budgetId);

    default FinBudgetItem findOne(Long budgetId, Long subjectId) {
        return selectOne(new LambdaQueryWrapper<FinBudgetItem>()
            .eq(FinBudgetItem::getBudgetId, budgetId)
            .eq(FinBudgetItem::getSubjectId, subjectId)
            .eq(FinBudgetItem::getDeleted, 0));
    }
}
