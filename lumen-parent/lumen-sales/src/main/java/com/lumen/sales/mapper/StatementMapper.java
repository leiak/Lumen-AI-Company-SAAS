package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Statement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatementMapper extends BaseMapper<Statement> {

    @Select("SELECT * FROM sal_statement "
        + "WHERE customer_id = #{customerId} "
        + "AND period_start = #{periodStart} AND period_end = #{periodEnd} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 LIMIT 1")
    Statement findByCustomerAndPeriod(@Param("customerId") Long customerId,
                                      @Param("periodStart") LocalDate periodStart,
                                      @Param("periodEnd") LocalDate periodEnd,
                                      @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_statement "
        + "WHERE customer_id = #{customerId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY period_start DESC")
    List<Statement> findByCustomer(@Param("customerId") Long customerId,
                                   @Param("tenantId") Long tenantId);
}