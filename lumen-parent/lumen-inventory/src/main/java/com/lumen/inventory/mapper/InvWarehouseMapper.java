package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvWarehouseMapper extends BaseMapper<InvWarehouse> {

    @Select("SELECT * FROM inv_warehouse WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    InvWarehouse findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT * FROM inv_warehouse WHERE tenant_id = #{tenantId} AND status = 'active' AND deleted = 0 ORDER BY id")
    List<InvWarehouse> findActive(@Param("tenantId") Long tenantId);
}