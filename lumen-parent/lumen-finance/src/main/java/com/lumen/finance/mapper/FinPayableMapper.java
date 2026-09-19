package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinPayable;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinPayableMapper extends BaseMapper<FinPayable> {

    /**
     * 按 tenant 列出 pending 或 partial 应付单（用于 period.close 校验）。
     */
    default List<FinPayable> listOpenForCloseCheck() {
        return selectList(new LambdaQueryWrapper<FinPayable>()
            .in(FinPayable::getStatus, "pending", "partial")
            .eq(FinPayable::getDeleted, 0));
    }
}
