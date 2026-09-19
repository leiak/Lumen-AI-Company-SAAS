package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcInquiry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProcInquiryMapper extends BaseMapper<ProcInquiry> {

    /**
     * 按 code 查唯一 (tenant scoped)。
     */
    @Select("SELECT * FROM proc_inquiry WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    ProcInquiry findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
}