package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvInout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvInoutMapper extends BaseMapper<InvInout> {

    @Select("SELECT * FROM inv_inout WHERE tenant_id = #{tenantId} AND type = #{type} AND deleted = 0 ORDER BY id DESC")
    List<InvInout> findByType(@Param("tenantId") Long tenantId, @Param("type") String type);

    @Select("SELECT * FROM inv_inout WHERE tenant_id = #{tenantId} "
        + "AND source_type = #{sourceType} AND source_id = #{sourceId} AND deleted = 0 ORDER BY id DESC")
    List<InvInout> findBySource(@Param("tenantId") Long tenantId,
                                @Param("sourceType") String sourceType,
                                @Param("sourceId") Long sourceId);

    @Select("SELECT * FROM inv_inout WHERE tenant_id = #{tenantId} "
        + "AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<InvInout> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);
}