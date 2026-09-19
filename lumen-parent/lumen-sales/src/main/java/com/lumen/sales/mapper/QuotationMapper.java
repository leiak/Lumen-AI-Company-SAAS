package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Quotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuotationMapper extends BaseMapper<Quotation> {

    @Select("SELECT * FROM sal_quotation "
        + "WHERE opportunity_id = #{opportunityId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY version DESC")
    List<Quotation> findByOpportunity(@Param("opportunityId") Long opportunityId,
                                      @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_quotation "
        + "WHERE opportunity_id = #{opportunityId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY version DESC LIMIT 1")
    Quotation findLatestByOpportunity(@Param("opportunityId") Long opportunityId,
                                      @Param("tenantId") Long tenantId);
}