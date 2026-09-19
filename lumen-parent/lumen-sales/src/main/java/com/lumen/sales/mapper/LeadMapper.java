package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Lead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LeadMapper extends BaseMapper<Lead> {

    @Select("SELECT * FROM sal_lead "
        + "WHERE owner_user_id = #{ownerUserId} AND status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Lead> findByOwnerAndStatus(@Param("ownerUserId") Long ownerUserId,
                                    @Param("status") String status,
                                    @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_lead "
        + "WHERE source = #{source} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Lead> findBySource(@Param("source") String source,
                            @Param("tenantId") Long tenantId);
}