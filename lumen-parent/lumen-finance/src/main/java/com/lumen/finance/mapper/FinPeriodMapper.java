package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinPeriod;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinPeriodMapper extends BaseMapper<FinPeriod> {

    /**
     * 查 (tenant, year, month) 唯一期间行。
     */
    default FinPeriod findByYearMonth(int year, int month) {
        return selectOne(new LambdaQueryWrapper<FinPeriod>()
            .eq(FinPeriod::getYear, year)
            .eq(FinPeriod::getMonth, month)
            .eq(FinPeriod::getDeleted, 0));
    }
}
