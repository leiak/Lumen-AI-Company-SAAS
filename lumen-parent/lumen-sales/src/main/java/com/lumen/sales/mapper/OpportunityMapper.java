package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Opportunity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface OpportunityMapper extends BaseMapper<Opportunity> {

    @Select("SELECT * FROM sal_opportunity "
        + "WHERE owner_user_id = #{ownerUserId} AND stage = #{stage} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Opportunity> findByOwnerAndStage(@Param("ownerUserId") Long ownerUserId,
                                          @Param("stage") String stage,
                                          @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_opportunity "
        + "WHERE customer_id = #{customerId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Opportunity> findByCustomer(@Param("customerId") Long customerId,
                                     @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_opportunity "
        + "WHERE status = 'open' AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Opportunity> findOpen(@Param("tenantId") Long tenantId);

    /** Funnel aggregation — group by stage. */
    @Select("SELECT stage, COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS total_amount "
        + "FROM sal_opportunity WHERE tenant_id = #{tenantId} AND deleted = 0 "
        + "AND stage IN ('qualification','proposal','negotiation','won','lost') "
        + "GROUP BY stage")
    List<Map<String, Object>> funnelByStage(@Param("tenantId") Long tenantId);

    /** This-month won amount. */
    @Select("SELECT COALESCE(SUM(amount),0) FROM sal_opportunity "
        + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND stage = 'won' "
        + "AND update_time >= #{startOfMonth}")
    BigDecimal thisMonthWonAmount(@Param("tenantId") Long tenantId,
                                  @Param("startOfMonth") java.time.LocalDateTime startOfMonth);
}