package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstStocktake;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AstStocktakeMapper extends BaseMapper<AstStocktake> {

    /**
     * 按期间+部门查询盘点单。Tenant filter by interceptor.
     */
    @Select("SELECT * FROM ast_stocktake WHERE period = #{period} "
        + "AND department_id = #{deptId} AND deleted = 0 ORDER BY id DESC")
    List<AstStocktake> findByPeriodAndDept(@Param("period") String period, @Param("deptId") Long deptId);
}