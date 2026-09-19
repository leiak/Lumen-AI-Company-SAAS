package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvLocationMapper extends BaseMapper<InvLocation> {

    @Select("SELECT * FROM inv_location WHERE tenant_id = #{tenantId} "
        + "AND warehouse_id = #{warehouseId} AND deleted = 0 ORDER BY code")
    List<InvLocation> findByWarehouse(@Param("tenantId") Long tenantId,
                                       @Param("warehouseId") Long warehouseId);
}