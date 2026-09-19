package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.QuotationItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuotationItemMapper extends BaseMapper<QuotationItem> {

    @Select("SELECT * FROM sal_quotation_item "
        + "WHERE quotation_id = #{quotationId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id ASC")
    List<QuotationItem> findByQuotation(@Param("quotationId") Long quotationId,
                                        @Param("tenantId") Long tenantId);
}