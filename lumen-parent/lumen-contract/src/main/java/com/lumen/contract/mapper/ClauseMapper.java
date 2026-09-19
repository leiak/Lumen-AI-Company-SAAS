package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.Clause;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClauseMapper extends BaseMapper<Clause> {

    @Select("SELECT * FROM ctr_clause WHERE contract_id = #{contractId} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY order_num ASC")
    List<Clause> findByContract(@Param("contractId") Long contractId,
                                 @Param("tenantId") Long tenantId);
}
