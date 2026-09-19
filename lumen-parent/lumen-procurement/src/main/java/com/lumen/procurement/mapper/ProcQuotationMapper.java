package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcQuotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcQuotationMapper extends BaseMapper<ProcQuotation> {

    /**
     * 按 inquiry_id 查询所有报价。
     */
    @Select("SELECT * FROM proc_quotation WHERE tenant_id = #{tenantId} "
        + "AND inquiry_id = #{inquiryId} AND deleted = 0 ORDER BY total_amount ASC")
    List<ProcQuotation> findByInquiry(@Param("tenantId") Long tenantId,
                                       @Param("inquiryId") Long inquiryId);

    /**
     * 按 supplier 查询报价历史。
     */
    @Select("SELECT * FROM proc_quotation WHERE tenant_id = #{tenantId} "
        + "AND supplier_id = #{supplierId} AND deleted = 0 ORDER BY id DESC")
    List<ProcQuotation> findBySupplier(@Param("tenantId") Long tenantId,
                                       @Param("supplierId") Long supplierId);
}